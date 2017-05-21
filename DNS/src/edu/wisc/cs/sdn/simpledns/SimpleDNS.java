package edu.wisc.cs.sdn.simpledns;
import edu.wisc.cs.sdn.simpledns.packet.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class SimpleDNS 
{
	public static DatagramSocket socket;
	public static Map<String, List<String>> ipMap = new HashMap<String, List<String>>();
	public static void main(String[] args) throws Exception
	{
        System.out.println("Hello, DNS!"); 
		if (args.length != 4) {
			System.err.println("Usage: java edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root server ip> -e <ec2 csv>");
			return;
		}		
		String rootIp = args[1];
		String file = args[3];

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
    		String line;
    		while ((line = br.readLine()) != null) {
				String city = line.split(",")[1];
				String [] ip = line.split(",")[0].split("/");
				List<String> entry = new ArrayList<String>();
				entry.add(ip[1]);
				entry.add(city);
				ipMap.put(ip[0], entry);	
			}
		} catch (Exception e) {
			System.err.println(e);
		}

	    InetAddress root = InetAddress.getByName(rootIp);
        socket = new DatagramSocket(8053);
        byte[] buffer = new byte[4096];
		while (true) {
				DatagramPacket queryPkt = new DatagramPacket(buffer, buffer.length);
				socket.receive(queryPkt);
				DNS dnsPkt = DNS.deserialize(queryPkt.getData(), queryPkt.getLength());
				if (dnsPkt.getOpcode() != DNS.OPCODE_STANDARD_QUERY) return;
				for (DNSQuestion q : dnsPkt.getQuestions()) {
					short type = q.getType();
					if (type != DNS.TYPE_A && type != DNS.TYPE_NS && type != DNS.TYPE_CNAME && type != DNS.TYPE_AAAA) return;
					DatagramPacket ansPkt;
					String origQ = q.getName();
					try {
						if (dnsPkt.isRecursionDesired()) ansPkt = recursive(queryPkt, root, q);
						else ansPkt = nonRecursive(queryPkt, root);
						ansPkt = postprocessPacket(ansPkt, queryPkt, origQ, type);
						ansPkt.setPort(queryPkt.getPort());
						ansPkt.setAddress(InetAddress.getByName("localhost"));
						socket.send(ansPkt);
					} catch (Exception e) {
						System.err.println(e);
					}
				}
		}
	}
	private static DatagramPacket recursive(DatagramPacket queryPkt, InetAddress root, DNSQuestion q) {
		DatagramPacket ansPkt = nonRecursive(queryPkt, root);
		DNS ans = DNS.deserialize(ansPkt.getData(), ansPkt.getLength());
		String name = "";
		InetAddress nextDst;
		List<DNSResourceRecord> cnameAnswers = new ArrayList<DNSResourceRecord>();	
		byte[] buffer = null;	
		while (ans.getAnswers().size() == 0 || ans.getAnswers().get(0).getType() == DNS.TYPE_CNAME) {
			//System.out.println(ans+"@@@@@@@@@\n");
			if (ans.getAdditional().size() > 0) {
				for (DNSResourceRecord rec : ans.getAdditional()) {
					if (rec.getType() != DNS.TYPE_AAAA && rec.getName().length() > 0) {
						name = rec.getData().toString();
						break;
					}
				}
			} else if (ans.getAuthorities().size() > 0) {
				name = ans.getAuthorities().get(0).getData().toString();
			} else if (ans.getAnswers().size() == 0) {
				break;
			}

			if (ans.getAdditional().size() == 1 && ans.getAdditional().get(0).getName().length() == 0 && ans.getAuthorities().size() == 0 && ans.getAnswers().size() == 0) break;
			try {
                nextDst = InetAddress.getByName(name);
			} catch (Exception e) {
                System.err.println(e);
                return null;
            }
			if (ans.getAnswers().size() > 0 && ans.getAnswers().get(0).getType() == DNS.TYPE_CNAME) {
				for (DNSResourceRecord cnameAns : ans.getAnswers()) {
					cnameAnswers.add(cnameAns);
				}
				String newQ = ans.getAnswers().get(0).getData().toString();
				DNS dnsPkt = DNS.deserialize(queryPkt.getData(), queryPkt.getLength());
				DNSQuestion newQuestion = new DNSQuestion(newQ, q.getType());
				List<DNSQuestion> DNSQuestions = new ArrayList<DNSQuestion>();
				DNSQuestions.add(newQuestion);
				dnsPkt.setQuestions(DNSQuestions);
				buffer = dnsPkt.serialize();
				queryPkt = new DatagramPacket(buffer, buffer.length);
			}
			ansPkt = nonRecursive(queryPkt, nextDst);
			ans = DNS.deserialize(ansPkt.getData(), ansPkt.getLength());
		}
		if (cnameAnswers.size() > 0) {
			ans = DNS.deserialize(ansPkt.getData(), ansPkt.getLength());
            List<DNSResourceRecord> DNSAnswers = ans.getAnswers();
            for (DNSResourceRecord cnameAns : cnameAnswers) {
				DNSAnswers.add(cnameAns);
			}
			ans.setAnswers(DNSAnswers);
            buffer = ans.serialize();
            ansPkt = new DatagramPacket(buffer, buffer.length);
		}
		return ansPkt;		
	}

	private static DatagramPacket nonRecursive(DatagramPacket queryPkt, InetAddress dst) {
		try {
			byte[] buffer = new byte[4096];
			DatagramPacket toSend = new DatagramPacket(queryPkt.getData(), queryPkt.getLength(), dst, 53);
			DatagramPacket toReceive = new DatagramPacket(buffer, buffer.length);
			socket.send(toSend);
			socket.receive(toReceive);
			return toReceive;
		} catch (Exception e) {
			System.err.println(e);
		}
		return null;
	}
	
	private static DatagramPacket postprocessPacket(DatagramPacket ansPkt, DatagramPacket queryPkt, String origQ, short type) {
		byte[] buffer = null;    	
		DNS ans = DNS.deserialize(ansPkt.getData(), ansPkt.getLength());
		DNSQuestion origQuestion = new DNSQuestion(origQ, type);
		List<DNSQuestion> DNSQuestions = new ArrayList<DNSQuestion>();
		DNSQuestions.add(origQuestion);
		ans.setQuestions(DNSQuestions);
		List<DNSResourceRecord> DNSAnswers = ans.getAnswers();
		List<DNSResourceRecord> updatedDNSAnswers = new ArrayList<DNSResourceRecord>();
		for (DNSResourceRecord dnsAns : DNSAnswers) {
			String ansIp = dnsAns.getData().toString();
			updatedDNSAnswers.add(dnsAns);
			if (dnsAns.getType() != DNS.TYPE_A) continue;
			for (String ip : ipMap.keySet()) {
				if ( (stringToInt(ansIp) ^ stringToInt(ip)) < (((long) 1) << (32 - Integer.parseInt(ipMap.get(ip).get(0))))) {
					//System.out.println(ansIp+" "+stringToInt(ansIp));
					//System.out.println(ip+" "+stringToInt(ip));
					DNSRdata dnsRDataString = new DNSRdataString(String.format("%s-%s", ipMap.get(ip).get(1), ansIp));
					DNSResourceRecord newAns = new DNSResourceRecord(dnsAns.getName(), DNS.TYPE_TXT, dnsRDataString);
					updatedDNSAnswers.add(newAns); 
					break;
				}
			}
		}
		ans.setAnswers(updatedDNSAnswers);
		buffer = ans.serialize();
		ansPkt = new DatagramPacket(buffer, buffer.length);
		return ansPkt;
	}	
	
	private static long stringToInt(String ip) {
		String[] array = ip.split("\\.");
		long addr = 0;
		for (int i=0; i<4; i++) {
			addr |= ( ((long)Integer.parseInt(array[i])) << (24 - i * 8) );
		} 
		return addr;
	}
}	


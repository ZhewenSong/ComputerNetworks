package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.lang.Math;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	//synchronized HashMap as Buffer
	final Map<Integer, LinkedList<EtherIface>> packetBuffer = Collections.synchronizedMap(new HashMap<Integer, LinkedList<EtherIface>>());

    //synchronized LinkedList to store RIPv2Entry to generate RIPv2
    final Map<Integer, RIPv2Entry> myEntries = Collections.synchronizedMap(new HashMap<Integer, RIPv2Entry>());

    //synchronized LinkedList to store RIPv2Entry to generate RIPv2
    final Map<Integer, timedRIPentry> learnedEntries = Collections.synchronizedMap(new HashMap<Integer, timedRIPentry>());


	private Timer timeoutThread;
	    
	class EtherIface{
		public Ethernet ether;
		public Iface inface;
		public Iface outIface;
		public EtherIface(Ethernet etherPacket, Iface inIface, Iface outIface){
			this.ether = etherPacket;
			this.inface = inIface;
			this.outIface = outIface;
		}
	}

    class timedRIPentry{
        public int ttl;
        public RIPv2Entry ripEntry;
        public timedRIPentry(int ttl, RIPv2Entry ripEntry){
            this.ttl = ttl;
            this.ripEntry = ripEntry;
        }
    }

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		timeoutThread = new Timer();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
    /**
     * @return void 
     *  to build routeTable
     */

    public void buildRipv2(){
        this.startRip();
    }

    /**
     * @return void 
     *  to start routeTable
     */
    public void startRip(){
        //construct interface RIP
        for(String s : this.interfaces.keySet()){
            Iface iface = this.interfaces.get(s);
            int mask = iface.getSubnetMask();
            int subnet = mask & iface.getIpAddress();
            this.routeTable.insert(subnet, 0, mask, iface);
            //make initial RIPv2Entry
            RIPv2Entry ripv2Entry = new RIPv2Entry(subnet, mask, 0);
            synchronized(myEntries){
                myEntries.put(subnet, ripv2Entry);
            }
            this.sendResquestRIP();
        }
        //sendUnsolicitedRIP
        sendRIPTimer sendTask = new sendRIPTimer();
        timeoutThread.schedule(sendTask, 0, 1000 * 10);

        timeoutRIP checkTime = new timeoutRIP();
        timeoutThread.schedule(checkTime, 0, 1000);
    }

    public class sendRIPTimer extends TimerTask{
        public sendRIPTimer(){}
        public void run(){
			System.out.println(routeTable.toString());
            System.out.println("Sending Unsol RIP every 10 secs");
			sendUnsolicitedRIP();
        }
    }


    public class timeoutRIP extends TimerTask{
        public timeoutRIP(){}
        public void run(){
            synchronized(learnedEntries){
                List<Integer> toRemove = new ArrayList<Integer>();
				for(Integer i : learnedEntries.keySet()){
                    timedRIPentry e = learnedEntries.get(i);
                    if(e.ttl == 0){
                        RIPv2Entry rip = e.ripEntry;
                        toRemove.add(i);
                        System.out.println(rip.toString()+"Timed out! ");
						routeTable.remove(rip.getAddress(), rip.getSubnetMask());
                        //sendUnsolicitedRIP();
                    }else{
                        e.ttl--;
                    }
                }
				for (Integer i : toRemove) {
                	synchronized(myEntries){
                    	myEntries.remove(i);
                    }
					learnedEntries.remove(i);
				}
            }
        }
    }

    /**
     *@return send UnsolicitedRIP
     *
     */
    void sendUnsolicitedRIP(){
        //TODO
        for(String s : this.interfaces.keySet()){
            Iface outIface = this.interfaces.get(s);
            int ip = IPv4.toIPv4Address("224.0.0.9");
            MACAddress mac = MACAddress.valueOf("FF:FF:FF:FF:FF:FF");
            Ethernet toSendEther = buildRipv2(RIPv2.COMMAND_RESPONSE, ip, mac, outIface);
            this.sendPacket(toSendEther, outIface);
        }
        return;
    }


    /**
     *@return send requestRIP
     *
     */
    void sendResquestRIP(){
        //TODO
        for(String s : this.interfaces.keySet()){
            Iface outIface = this.interfaces.get(s);
            int ip = IPv4.toIPv4Address("224.0.0.9");
            MACAddress mac = MACAddress.valueOf("FF:FF:FF:FF:FF:FF");
            Ethernet toSendEther = buildRipv2(RIPv2.COMMAND_REQUEST, ip, mac, outIface);
            this.sendPacket(toSendEther, outIface);
			System.out.println("Sent request "+toSendEther.toString());
        }
        return;
    }

    /**
     *@return RIPv2 buildRipv2 packet as needed
     *
     */
    public Ethernet buildRipv2(byte command, int ip, MACAddress mac, Iface outIface){
        RIPv2 inUDP = new RIPv2();
        inUDP.setCommand(command);
        for(Integer i : myEntries.keySet()){
            inUDP.addEntry(myEntries.get(i));
        }
        //build UDP
        UDP inIP = new UDP();
        inIP.setSourcePort(UDP.RIP_PORT);
        inIP.setDestinationPort(UDP.RIP_PORT);
        inIP.setPayload(inUDP);
        //build IP
        IPv4 inEther = new IPv4();
        inEther.setProtocol(IPv4.PROTOCOL_UDP);
        inEther.setDestinationAddress(ip);
        inEther.setSourceAddress(outIface.getIpAddress());
        inEther.setPayload(inIP);
        //build Ether
        Ethernet ripPacket = new Ethernet();
        ripPacket.setEtherType(Ethernet.TYPE_IPv4);
		ripPacket.setDestinationMACAddress(mac.toBytes());
        ripPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());
        ripPacket.setPayload(inEther);
		return ripPacket;
    }

	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
            if(this.isRIP(etherPacket, inIface)){
                this.handleRipv2(etherPacket, inIface);
            } else {
				this.handleIpPacket(etherPacket, inIface);
			}
			break;
		case Ethernet.TYPE_ARP:
			this.handleArpPacket(etherPacket, inIface);
			break;
		}
		
		/********************************************************************/
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ 
			if(etherPacket.getEtherType() != Ethernet.TYPE_ARP)
			{
				this.handleArpPacket(etherPacket, inIface);
			}
			else
			{
				return;
			} 
		}
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { return; }
        
        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        if (0 == ipPacket.getTtl())
        { //TODO
        	Ethernet timeExceedICMP = this.generateICMP(etherPacket, inIface, (byte)11, (byte)0);
        	this.sendPacket(timeExceedICMP, inIface);
        	return; 
        }
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress())
        	{ //TODO
        		if(ipPacket.getProtocol() == IPv4.PROTOCOL_TCP || ipPacket.getProtocol() == IPv4.PROTOCOL_UDP){
        			System.out.println("Destination Host Unreachable");
					Ethernet destiUnreachableICMP = this.generateICMP(etherPacket, inIface, (byte)3, (byte)3);
        			this.sendPacket(destiUnreachableICMP, inIface);
        			return;
        		} else if(ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP && ((ICMP)ipPacket.getPayload()).getIcmpType() == (byte)8){
        			Ethernet echoICMP = generateEchoICMP(etherPacket, inIface);
        			this.sendPacket(echoICMP, inIface);
        		}
        		 return;
        	}
        }
		
        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
	}

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        System.out.println("Forward IP packet");
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
         System.out.println("bestmatch: "+bestMatch);
		if (null == bestMatch)
        { //TODO
        	Ethernet destiUnreachableICMP = this.generateICMP(etherPacket, inIface, (byte)3, (byte)0);
        	System.out.println("dest unreachable");
			this.sendPacket(destiUnreachableICMP, inIface);
        	return; }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        { //TODO
        	this.bufferPacket(etherPacket, inIface, outIface, nextHop);
        	return;
		}
		
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }

    private Ethernet generateICMP(Ethernet etherPacket, Iface inIface, byte icmpType, byte icmpCode){
    	Ethernet ether = new Ethernet();
    	IPv4 ip = new IPv4();
    	ICMP icmp = new ICMP();
    	Data data = new Data();
    	IPv4 oldIP = (IPv4)etherPacket.getPayload();
    	//prepare ethernet header
    	ether.setEtherType(Ethernet.TYPE_IPv4);
    	//find and set source MAC
    	ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
    	ether.setDestinationMACAddress(etherPacket.getSourceMACAddress());
    	//prepare IP header
    	//set TTL to 64
    	ip.setTtl((byte)64);
    	//set protocol
    	ip.setProtocol(IPv4.PROTOCOL_ICMP);
    	//set source address
    	ip.setSourceAddress(inIface.getIpAddress());
    	//set destination address
    	ip.setDestinationAddress(oldIP.getSourceAddress());
    	//prepare ICMP header
    	//set Type
    	icmp.setIcmpType(icmpType);
    	//set Code
    	icmp.setIcmpCode(icmpCode);
    	//prepare data
    	int size = oldIP.getHeaderLength() * 4;
    	byte[] oldIPdata = oldIP.serialize();
    	byte[] payload = new byte[12 + size];
    	for (int i = 4; i < 12 + size; i++){
    		payload[i] = oldIPdata[i - 4];
    	}
    	//set payload
    	data.setData(payload);
    	icmp.setPayload(data);
    	ip.setPayload(icmp);
    	ether.setPayload(ip);
    	return ether;
    }

    private Ethernet generateEchoICMP(Ethernet etherPacket, Iface inIface){
    	Ethernet ether = new Ethernet();
    	IPv4 ip = new IPv4();
    	ICMP icmp = new ICMP();
    	IPv4 oldIP = (IPv4)etherPacket.getPayload();
    	//prepare ethernet header
    	ether.setEtherType(Ethernet.TYPE_IPv4);
    	//find and set source MAC
    	ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
    	ether.setDestinationMACAddress(etherPacket.getSourceMACAddress());
    	//prepare IP header
    	//set TTL to 64
    	ip.setTtl((byte)64);
    	//set protocol
    	ip.setProtocol(IPv4.PROTOCOL_ICMP);
    	//set source address
    	ip.setSourceAddress(oldIP.getDestinationAddress());
    	//set destination address
    	ip.setDestinationAddress(oldIP.getSourceAddress());
    	//prepare ICMP header
    	//set Type
    	icmp.setIcmpType((byte)0);
    	//set Code
    	icmp.setIcmpCode((byte)0);
    	icmp.setPayload(oldIP.getPayload().getPayload());
    	ip.setPayload(icmp);
    	ether.setPayload(ip);
    	return ether;
    }

    private void handleArpPacket(Ethernet etherPacket, Iface inIface){
    	ARP arpPacket = (ARP)etherPacket.getPayload();
		int targetIp = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();
		//handle ARP.OP_REQUEST
		if(arpPacket.getOpCode() == ARP.OP_REQUEST){
			if(targetIp == inIface.getIpAddress()){
				Ethernet arpReply = this.generateArpReply(etherPacket, inIface);
				this.sendPacket(arpReply, inIface);
			}
			else{
				return;
			}
		}
		if(arpPacket.getOpCode() == ARP.OP_REPLY){
			this.arpReplyHandler(etherPacket, inIface);
		}

    }

    private void bufferPacket(Ethernet etherPacket, Iface inIface, Iface outIface, int ip){
    	EtherIface toPut = new EtherIface(etherPacket, inIface, outIface);
    	synchronized(this.packetBuffer){
    		if(this.packetBuffer.containsKey(ip))
        	{
				System.out.println("later "+this.packetBuffer.size());
				this.packetBuffer.get(ip).add(toPut);
			}
        	else
        	{
				System.out.println("first "+this.packetBuffer.size());
        		LinkedList<EtherIface> newList = new LinkedList<EtherIface>();
        		newList.add(toPut);
        		this.packetBuffer.put(ip, newList);
        		//TODO
				System.out.println("IP to queue: "+IPv4.fromIPv4Address(ip));
        		TimerTask arp_request = new arpTask(outIface, ip);
        		timeoutThread.schedule(arp_request, 0, 1000);
        	}
    	}
    	return;
    }


    private Ethernet generateArpReply(Ethernet etherPacket, Iface inIface){
    	ARP oldArp = (ARP)etherPacket.getPayload();
    	Ethernet ether = new Ethernet();
    	ARP arp = new ARP();
    	//set Ethernet header
    	//set Ethernet type
    	ether.setEtherType(Ethernet.TYPE_ARP);
    	//set Source MAC address
    	ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
    	//set Destination MAC address
    	ether.setDestinationMACAddress(etherPacket.getSourceMAC().toString());
    	//set ARP header
    	//set Hardware Type
    	arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
    	//set Protocol Type
    	arp.setProtocolType(ARP.PROTO_TYPE_IP);
    	//set Hardware address length
    	arp.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
    	//set Protocol address length
    	arp.setProtocolAddressLength((byte)4);
    	//set Opcode
    	arp.setOpCode(ARP.OP_REPLY);
    	//set sender hardware address
    	arp.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
    	//set sender protocol address
    	arp.setSenderProtocolAddress(inIface.getIpAddress());
    	//set Target hardware address
    	arp.setTargetHardwareAddress(oldArp.getSenderHardwareAddress());
    	//set Target protocol address
    	arp.setTargetProtocolAddress(oldArp.getSenderProtocolAddress());
    	//set payload
    	ether.setPayload(arp);
    	return ether;
    }




    private Ethernet generateArpRequest(Iface outIface, int nextHop){
    	byte[] newMAC = new byte[6];
    	Arrays.fill(newMAC, (byte)0xff);
    	byte[] hardwareAddress = new byte[6];
    	Arrays.fill(hardwareAddress, (byte)0);
    	Ethernet ether = new Ethernet();
    	ARP arp = new ARP();
    	//set Ethernet header
    	//set Ethernet type
    	ether.setEtherType(Ethernet.TYPE_ARP);
    	//set Source MAC address
    	ether.setSourceMACAddress(outIface.getMacAddress().toBytes());
    	//set Destination MAC address
    	ether.setDestinationMACAddress(newMAC);
    	//set ARP header
    	//set Hardware Type
    	arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
    	//set Protocol Type
    	arp.setProtocolType(ARP.PROTO_TYPE_IP);
    	//set Hardware address length
    	arp.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
    	//set Protocol address length
    	arp.setProtocolAddressLength((byte)4);
    	//set Opcode
    	arp.setOpCode(ARP.OP_REQUEST);
    	//set sender hardware address
    	arp.setSenderHardwareAddress(outIface.getMacAddress().toBytes());
    	//set sender protocol address
    	arp.setSenderProtocolAddress(outIface.getIpAddress());
    	//set Target hardware address
    	arp.setTargetHardwareAddress(hardwareAddress);
    	//set Target protocol address
    	arp.setTargetProtocolAddress(nextHop);
    	//set payload
    	ether.setPayload(arp);
    	return ether;
    }


    void sendEmptyArpEntryICMP(Ethernet etherPacket, Iface inIface){
    	System.out.println("empty arp packet before: "+etherPacket.toString());
		
		Ethernet hostUnreachableICMP = this.generateICMP(etherPacket, inIface, (byte)3, (byte)1);
        
		System.out.println("empty arp packet after: "+hostUnreachableICMP.toString());
		this.sendPacket(hostUnreachableICMP, inIface);
        return; 
    }

    public class arpTask extends TimerTask{
    	public int sentTime = 0;
    	public Iface outIface;
    	public int ip;

    	public arpTask(Iface outIface, int ip){
    		this.outIface = outIface;
    		this.ip = ip;
    	}

    	public void run(){
    		synchronized(packetBuffer){
    			if(packetBuffer.containsKey(ip)){
    				if(sentTime == 3)
    				{
						System.out.println("3 times");
    					cantFindArp(ip);
    					cancel();
    					return;
    				}
    				else
    				{
						System.out.println("Sent for "+sentTime+" times");
    					sendArpRequest(outIface, ip);
    					sentTime++;
    				}
    			}
    			else{
    				cancel();
    				return;
    			}
    		}
    	}
    }


    void sendArpRequest(Iface outIface, int nextHop){
    	//TODO
    	Ethernet arpRequest = this.generateArpRequest(outIface, nextHop);
    	this.sendPacket(arpRequest, outIface);
    	return;
    }

    void cantFindArp(int ip){
    	synchronized(packetBuffer){
    		LinkedList<EtherIface> toDrop = packetBuffer.get(ip);
    		packetBuffer.remove(ip);
    		for(EtherIface e : toDrop){
    			System.out.println("empty arp entry in cantFindArp: "+IPv4.fromIPv4Address(ip));
				sendEmptyArpEntryICMP(e.ether, e.inface);
    		}
    	}
    	return;
    }

    private void arpReplyHandler(Ethernet etherPacket, Iface inIface){
    	ARP arpPacket = (ARP)etherPacket.getPayload();
		int sourceIp = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
        this.insertArpCacheEntry(new MACAddress(arpPacket.getSenderHardwareAddress()), sourceIp);
		System.out.println("src ip "+IPv4.fromIPv4Address(sourceIp));
		synchronized(packetBuffer){
			if(packetBuffer.containsKey(sourceIp)){
				LinkedList<EtherIface> toForward = this.packetBuffer.get(sourceIp);
				this.packetBuffer.remove(sourceIp);
				for(EtherIface e : toForward){
					e.ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
					e.ether.setDestinationMACAddress(arpPacket.getSenderHardwareAddress());
					this.sendPacket(e.ether, inIface);
				}
			}
			else
				return;
		}
		return;
    }

    void insertArpCacheEntry(MACAddress mac, int ip) {
        this.arpCache.insert(mac, ip);
        return;
    }

    public void handleRipv2(Ethernet ether, Iface inIface){
        //TODO
        IPv4 ip = (IPv4)ether.getPayload();
        UDP udp = (UDP) ip.getPayload();
        RIPv2 rip = (RIPv2) udp.getPayload();
        if(rip.getCommand() == RIPv2.COMMAND_REQUEST){
            Ethernet toSend = this.buildRipv2(RIPv2.COMMAND_RESPONSE, ip.getSourceAddress(), ether.getSourceMAC(), inIface);
            System.out.println("************************************* Handling RIP"+toSend.toString());
			this.sendPacket(toSend, inIface);
        }
        else{
            for(RIPv2Entry e : rip.getEntries()){
                //no entry in Route Table
                if(this.routeTable.lookup(e.getAddress()) == null){
                    this.routeTable.insert(e.getAddress(), ip.getSourceAddress(), e.getSubnetMask(), inIface);
                    e.setMetric(e.getMetric() + 1);
                    synchronized(myEntries){
                        myEntries.put(e.getAddress(), e);
                    }
                    synchronized(learnedEntries){
                        timedRIPentry toAdd = new timedRIPentry(30, e);
                        learnedEntries.put(e.getAddress(), toAdd);
                    }
                    //send unsolicited RIP
                    //System.out.println("Sending Unsol RIP In handle RIP - new entry added");
					//this.sendUnsolicitedRIP();
                }
                else{
                    //if Route table contains this subnet, but with higher metric
                    synchronized(myEntries){
                        int oldMetric = myEntries.get(e.getAddress()).getMetric();
                        if(oldMetric > 1 + e.getMetric()){
                            myEntries.put(e.getAddress(), e);
                            this.routeTable.update(e.getAddress(), e.getSubnetMask(), ip.getSourceAddress(), inIface);
                            synchronized(learnedEntries){
                                timedRIPentry toAdd = new timedRIPentry(30, e);
                                learnedEntries.put(e.getAddress(), toAdd);
                            }
							//System.out.println("Sending Unsol RIP In handle RIP - entry updated");
                            //this.sendUnsolicitedRIP();
                        }
                        else if(oldMetric == 1 + e.getMetric()){
                            synchronized(learnedEntries){
                                timedRIPentry toAdd = new timedRIPentry(30, e);
                                learnedEntries.put(e.getAddress(), toAdd);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean isRIP(Ethernet ether, Iface inIface){
		IPv4 ipPacket = (IPv4)ether.getPayload();
		int ip = ipPacket.getDestinationAddress();
		int multi = IPv4.toIPv4Address("224.0.0.9");
		if (multi == ip || inIface.getIpAddress() == ip) {
	    	if ( ((IPv4)ipPacket).getProtocol() == IPv4.PROTOCOL_UDP ) { 
				UDP udpPacket = (UDP)ipPacket.getPayload();
				if (udpPacket.getDestinationPort() == UDP.RIP_PORT) {
					return true;
				}
			}
		}
		return false;
	}	
    
}

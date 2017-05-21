package edu.wisc.cs.sdn.vnet.sw;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.LinkedList;
import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	
	public HashMap<String,SwitchEntry> switchTable = new HashMap<String,SwitchEntry>();
	final int maxTimeToLive = 15;
	public Timer timer = new Timer();
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public synchronized void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		/********************************************************************/
		
		Map<String,Iface> interfaces = getInterfaces();
		String dst = etherPacket.getDestinationMAC().toString();
		String src = etherPacket.getSourceMAC().toString();
		if (switchTable.isEmpty()) { 
			timer.schedule(new DecTime(), 1000);
		}
		if (!switchTable.containsKey(src)) {
			switchTable.put(src, new SwitchEntry(inIface, maxTimeToLive));
		} else { // refresh the TTL whenever the packet is transmitted
			switchTable.get(src).timeToLive = maxTimeToLive;
		}
		if (switchTable.containsKey(dst)) {
			System.out.println("Found "+dst+" -> "+switchTable.get(dst).iface.toString());
			SwitchEntry switchEntry = switchTable.get(dst);
			Iface nextIface = switchEntry.iface;
			sendPacket(etherPacket, nextIface);
		} else {  // dst not found in switch table, bcast/flood the rest interfaces
		for (Map.Entry<String,Iface> entry : interfaces.entrySet()) {
			if (!entry.getKey().equals(inIface.toString())) {
				sendPacket(etherPacket, entry.getValue());
			}
		}
		}
		for (Map.Entry<String,SwitchEntry> entry : switchTable.entrySet()) {
			System.out.println(entry.getKey()+" : "+entry.getValue().iface.toString());
		}
		
	}
	class DecTime extends TimerTask {
		public void run() {
		LinkedList<String> timeoutList = new LinkedList<String>();
		for (Map.Entry<String,SwitchEntry> entry : switchTable.entrySet()) {
			System.out.println(entry.getKey()+" : "+entry.getValue().timeToLive+"s to live.");
			if (entry.getValue().timeToLive > 0)
				entry.getValue().timeToLive--;
			else
				timeoutList.add(entry.getKey());
		}
		for (String s : timeoutList) {  // Remove timeout host from switch table
			switchTable.remove(s);
			System.out.println(s + " removed due to timeout");
		}
		if (switchTable.isEmpty()) {
			//timer.cancel();
			return;
		}
		timer.schedule(new DecTime(), 1000);
		}
	}
}


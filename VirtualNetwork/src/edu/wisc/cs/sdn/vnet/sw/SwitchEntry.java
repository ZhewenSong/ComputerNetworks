package edu.wisc.cs.sdn.vnet.sw;
import java.util.HashMap;
import edu.wisc.cs.sdn.vnet.Iface;

public class SwitchEntry {
	public Iface iface;
	public double timeToLive;
	public SwitchEntry(Iface iface, double timeToLive) {
		this.iface = iface;
		this.timeToLive = timeToLive;
	}
}

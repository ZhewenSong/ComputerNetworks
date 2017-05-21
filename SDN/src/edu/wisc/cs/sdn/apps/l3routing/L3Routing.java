package edu.wisc.cs.sdn.apps.l3routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.util.Host;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;

public class L3Routing implements IFloodlightModule, IOFSwitchListener, 
		ILinkDiscoveryListener, IDeviceListener
{
	public static final String MODULE_NAME = L3Routing.class.getSimpleName();
	
	// Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);
    
    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;

    // Interface to link discovery service
    private ILinkDiscoveryService linkDiscProv;

    // Interface to device manager service
    private IDeviceService deviceProv;
    
    // Switch table in which rules should be installed
    public static byte table;
    
    // Map of hosts to devices
    private Map<IDevice,Host> knownHosts;

	// 2D map -- (A -> B) mapped to the outPort of A
	private Map<Long,Map<Long,Link>> predMaps;

	// Map of host to the switchId it's attached
	//private Map<Host,Long> hostMap;
     
	public static final int INF = 0xffff;
	/**
     * Loads dependencies and initializes data structures.
     */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		Map<String,String> config = context.getConfigParams(this);
        table = Byte.parseByte(config.get("table"));
 
		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
        this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);
        
        this.knownHosts = new ConcurrentHashMap<IDevice,Host>();
        this.predMaps = new ConcurrentHashMap<Long,Map<Long,Link>>();	
		//this.hostMap = new ConcurrentHashMap<Host,Long>();
	}

	/**
     * Subscribes to events and performs other startup tasks.
     */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.linkDiscProv.addListener(this);
		this.deviceProv.addListener(this);
		
		/*********************************************************************/
		/* TODO: Initialize variables or perform startup tasks, if necessary */

		/*********************************************************************/

	}
	
    /**
     * Get a list of all known hosts in the network.
     */
    private Collection<Host> getHosts()
    { return this.knownHosts.values(); }
	
    /**
     * Get a map of all active switches in the network. Switch DPID is used as
     * the key.
     */
	private Map<Long, IOFSwitch> getSwitches()
    { return floodlightProv.getAllSwitchMap(); }
	
    /**
     * Get a list of all active links in the network.
     */
    private Collection<Link> getLinks()
    { return linkDiscProv.getLinks().keySet(); }

    /**
     * Event handler called when a host joins the network.
     * @param device information about the host
     */
	@Override
	public void deviceAdded(IDevice device) 
	{
		Host host = new Host(device, this.floodlightProv);
		// We only care about a new host if we know its IP
		if (host.getIPv4Address() != null)
		{
			log.info(String.format("Host %s added", host.getName()));
			this.knownHosts.put(device, host);
			//this.hostMap.put(host, host.getSwitch().getId());

			/*****************************************************************/
			/* TODO: Update routing: add rules to route to new host          */
			updateHost(host);
			//printMap();
			/*****************************************************************/
		
		}
	}

	/**
     * Event handler called when a host is no longer attached to a switch.
     * @param device information about the host
     */
	@Override
	public void deviceRemoved(IDevice device) 
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{ return; }
		
		this.knownHosts.remove(device);

		log.info(String.format("Host %s is no longer attached to a switch", 
				host.getName()));
		/*********************************************************************/
		/* TODO: Update routing: remove rules to route to host               */
		
		Map<Long, IOFSwitch> switches = this.getSwitches();
		for (IOFSwitch sw : switches.values()) {
			removeRules(sw, host);
		}

		/*********************************************************************/
	}

	/**
     * Event handler called when a host moves within the network.
     * @param device information about the host
     */
	@Override
	public void deviceMoved(IDevice device) 
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
			//this.hostMap.put(host, host.getSwitch().getId());
		}
		//log.info("In deviceMoved: "+ host.getName());	
		if (!host.isAttachedToSwitch())
		{
			this.deviceRemoved(device);
			return;
		}
		log.info(String.format("Host %s moved to s%d:%d", host.getName(),
				host.getSwitch().getId(), host.getPort()));
		
		/*********************************************************************/
		/* TODO: Update routing: change rules to route to host               */
		updateHost(host);
		/*********************************************************************/
	}
	
    /**
     * Event handler called when a switch joins the network.
     * @param DPID for the switch
     */
	@Override		
	public void switchAdded(long switchId) 
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		updateAllHosts();	
		/*********************************************************************/
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId) 
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d removed", switchId));
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		updateAllHosts();	
		/*********************************************************************/
	}

	/**
	 * Event handler called when multiple links go up or down.
	 * @param updateList information about the change in each link's state
	 */
	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList) 
	{
		for (LDUpdate update : updateList)
		{
			// If we only know the switch & port for one end of the link, then
			// the link must be from a switch to a host
			if (0 == update.getDst())
			{
				log.info(String.format("Link s%s:%d -> host updated", 
					update.getSrc(), update.getSrcPort()));
			}
			// Otherwise, the link is between two switches
			else
			{
				log.info(String.format("Link s%s:%d -> s%s:%d updated", 
					update.getSrc(), update.getSrcPort(),
					update.getDst(), update.getDstPort()));
			}
		}
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		updateAllHosts();
		/*********************************************************************/
	}


	private void updateHost(Host host) {
		if (!host.isAttachedToSwitch())	return;
		//long dst = this.hostMap.get(host);	
		long dst = host.getSwitch().getId();
		Collection<Host> hosts = this.getHosts();
		Map<Long,IOFSwitch> switches = this.getSwitches();

		if (!this.predMaps.containsKey(dst))
			this.predMaps.put(dst, computeShortestPaths(dst));
		for (Long src : switches.keySet()) {
			if (src == dst) { // at the same switch
				installRule(switches.get(src), host, host.getPort());
			} else {
				if (!this.predMaps.containsKey(dst)) continue;
				if (!this.predMaps.get(dst).containsKey(src)) continue;
				installRule(switches.get(src), host, 
							this.predMaps.get(dst).get(src).getDstPort());	
			}
		}
	}

	private void updateAllHosts() {
		Collection<Host> hosts = this.getHosts();
        Map<Long, IOFSwitch> switches = this.getSwitches();
		
		this.predMaps = new ConcurrentHashMap<Long,Map<Long,Link>>();
        for (Long source : switches.keySet()) {
            this.predMaps.put(source, computeShortestPaths(source));
			for (Host host : hosts) {
				removeRules(switches.get(source), host);
			}
		}

        for (Host host : hosts) {
            updateHost(host);
        }
	}

	private Map<Long,Link> computeShortestPaths(long source) {
		Collection<Link> links = this.getLinks();
		Map<Long, IOFSwitch> switches = this.getSwitches();

    	// Map of switch DPID to the link between it and its predecessor DPID
    	Map<Long,Link> predecessors = new ConcurrentHashMap<Long,Link>();

    	// Map of switch DPID to its shortest distance
  		Map<Long,Integer> distances = new ConcurrentHashMap<Long,Integer>();

  		// Initialize
  		for (long s: switches.keySet()) {
  			if (s == source) distances.put(s, 0);
  			else distances.put(s, INF);
  		}

		for (int i=1; i < switches.size(); i++) {
			for (Link link : links) {
				if (!distances.containsKey(link.getSrc()) || !distances.containsKey(link.getDst())) continue;
				if (distances.get(link.getSrc()) + 1 < distances.get(link.getDst())) {
					distances.put(link.getDst(), distances.get(link.getSrc()) + 1);
					predecessors.put(link.getDst(), link);
				}
			}
		}
		
		return predecessors;	
	}
	
	private void installRule(IOFSwitch sw, Host dst, int outPort) {
		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkDestination(dst.getIPv4Address());
		OFAction action = new OFActionOutput(outPort);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);
		OFInstruction instruction = new OFInstructionApplyActions(actions);
		List<OFInstruction> instructions = new ArrayList<OFInstruction>();
		instructions.add(instruction);
		SwitchCommands.installRule(sw, table, SwitchCommands.DEFAULT_PRIORITY, matchCriteria, instructions);
	}

    private void removeRules(IOFSwitch sw, Host dst) {
        OFMatch matchCriteria = new OFMatch();
        matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
        matchCriteria.setNetworkDestination(dst.getIPv4Address());
        SwitchCommands.removeRules(sw, table, matchCriteria);
    }
	
	private void printMap() {
		for (Long src : this.predMaps.keySet()) {
			for (Long dst : this.predMaps.get(src).keySet()) {
				System.out.println(String.format("s%s->s%s : %s", src, dst, this.predMaps.get(src).get(dst)));
			}
		}
	}
	
	/**
	 * Event handler called when link goes up or down.
	 * @param update information about the change in link state
	 */
	@Override
	public void linkDiscoveryUpdate(LDUpdate update) 
	{ this.linkDiscoveryUpdate(Arrays.asList(update)); }
	
	/**
     * Event handler called when the IP address of a host changes.
     * @param device information about the host
     */
	@Override
	public void deviceIPV4AddrChanged(IDevice device) 
	{ this.deviceAdded(device); }

	/**
     * Event handler called when the VLAN of a host changes.
     * @param device information about the host
     */
	@Override
	public void deviceVlanChanged(IDevice device) 
	{ /* Nothing we need to do, since we're not using VLANs */ }
	
	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId) 
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId) 
	{ /* Nothing we need to do */ }
	
	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * @param DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) 
	{ /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */ }

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName() 
	{ return this.MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(String type, String name) 
	{ return false; }

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(String type, String name) 
	{ return false; }
	
    /**
     * Tell the module system which services we provide.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() 
	{ return null; }

	/**
     * Tell the module system which services we implement.
     */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> 
			getServiceImpls() 
	{ return null; }

	/**
     * Tell the module system which modules we depend on.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> 
			getModuleDependencies() 
	{
		Collection<Class<? extends IFloodlightService >> floodlightService =
	            new ArrayList<Class<? extends IFloodlightService>>();
        floodlightService.add(IFloodlightProviderService.class);
        floodlightService.add(ILinkDiscoveryService.class);
        floodlightService.add(IDeviceService.class);
        return floodlightService;
	}
}


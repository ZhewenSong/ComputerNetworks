# ComputerNetworks

Course projects for CS640
1. Iperfer: An application that uses Java sockets API to transmit and receive data across a network and measure network performances
2. Virtual switch/router: 
* Construct a learning switch that optimally forwards packets based on link layer headers
* Determine the matching route table entry for a given IP address
* Develop a router that updates and forwards packets based on network layer headers
* Generate Internet Control Messaging Protocol (ICMP) messages when error conditions occur
* Populate the ARP cache by generating and consuming Address Resolution Protocol (ARP) messages
* Build a routing table using distance vector routing, and the virtual router no longer depends on a static ARP cache or static route table, and is pingable and traceble
3. Software Defined Networking:
* Implement a layer-3 routing application will install rules in SDN switches to forward traffic to hosts using the shortest, valid path (based on Bellman-Ford algorithm) through the network
* Implement a distributed load balancer application will redirect new TCP connections to hosts in a round-robin fashion
4. Domain Name System: A simple DNS server that performs recursive DNS resolutions, and appends a special annotation if an IP address belongs to an Amazon EC2 region

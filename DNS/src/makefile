JC = javac
JFLAGS = -g

SimpleDNS : edu/wisc/cs/sdn/simpledns/SimpleDNS.java
	$(JC) $(JFLAGS) edu/wisc/cs/sdn/simpledns/SimpleDNS.java

run:
	java edu.wisc.cs.sdn.simpledns.SimpleDNS -r a.root-servers.net -e ec2.csv  

clean:
	$(RM) *.class	

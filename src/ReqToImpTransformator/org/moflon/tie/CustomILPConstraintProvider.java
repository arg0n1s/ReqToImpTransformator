package ReqToImpTransformator.org.moflon.tie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPConstraint;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPConstraintProvider;
import org.moflon.tgg.algorithm.datastructures.ConsistencyCheckPrecedenceGraph;
import org.moflon.tgg.runtime.CCMatch;

import implementation.Cable;
import implementation.Computer;
import implementation.Container;
import implementation.Router;
import implementation.Server;
import requirements.Consumer;
import requirements.Provider;

public class CustomILPConstraintProvider implements UserDefinedILPConstraintProvider {
	// Hackety hack hack...
	public static LinkedList<EObject> computers;
	public static LinkedList<EObject> servers;
	public static LinkedList<EObject> vertices;
	public static LinkedList<EObject> edges;
	public static HashMap<EObject, LinkedList<EObject>> serverToProviderMapping;
	public static HashMap<EObject, LinkedList<EObject>> computerToConsumerMapping;
	public static HashMap<EObject, FindShortestPath> paths;
	
	// An elegant way to say: screw pattern matching we're going to do this our way.
	private static void initHack(Container c){
		computers = new LinkedList<EObject>();
		servers = new LinkedList<EObject>();
		vertices = new LinkedList<EObject>();
		edges = new LinkedList<EObject>();
		
		for(EObject current: c.eContents()){
			if(current.eClass().getInstanceClassName().contains("Cable")){
				edges.add(current);
			}else{
				vertices.add(current);
			}
			if(current.eClass().getInstanceClassName().contains("Computer")){
				computers.add(current);
			}
			else if(current.eClass().getInstanceClassName().contains("Server")){
				servers.add(current);
			}
		}
	}
	// .. what the signature says
	private static void initMappings(){
		serverToProviderMapping = new HashMap<EObject, LinkedList<EObject>>();
		computerToConsumerMapping = new HashMap<EObject, LinkedList<EObject>>();
	}
	// .. add found provider to server mappings
	private static void addServerMapping(EObject server, EObject provider){
		if(provider == null){
			return;
		}
		LinkedList<EObject> ll;
		if(!serverToProviderMapping.containsKey(server)){
			ll = new LinkedList<EObject>();
		}else{
			ll = serverToProviderMapping.get(server);
		}
		ll.add(provider);
		serverToProviderMapping.put(server, ll);
	}
	// .. add found consumer to computer mappings
	private static void addComputerMapping(EObject computer, EObject consumer){
		if(consumer == null){
			return;
		}
		LinkedList<EObject> ll;
		if(!computerToConsumerMapping.containsKey(computer)){
			ll = new LinkedList<EObject>();
		}else{
			ll = computerToConsumerMapping.get(computer);
		}
		ll.add(consumer);
		computerToConsumerMapping.put(computer, ll);
	}
	// Brute force, best force ..
	private static void findAllRoutes(){
		paths = new HashMap<EObject, FindShortestPath>();
		for(EObject server : servers){
			FindShortestPath fp = new FindShortestPath();
			fp.findShortestRoutes(server);
			paths.put(server, fp);
		}
	}

	@Override
	public Collection<UserDefinedILPConstraint> getUserDefinedConstraints(ConsistencyCheckPrecedenceGraph protocol) {
		//System.out.println("CSP");
		// Hack begins here
		initMappings();
		// End Hack
		Map<CCMatch, Integer> serverMatchesMap = new HashMap<>();
		Map<CCMatch, Integer> computerMatchesMap = new HashMap<>();
		Map<CCMatch, Integer> implementationRequirementMatchesMap = new HashMap<>();
		for (CCMatch m : protocol.getMatches()) {
			//System.out.println(m.getRuleName());

			if (m.getRuleName().equals("ReqProviderToServerRule")) {
				serverMatchesMap.put(m, protocol.matchToInt(m));
			} else if (m.getRuleName().equals("ReqConsumerToComputerRule")) {
				computerMatchesMap.put(m, protocol.matchToInt(m));
			} else if (m.getRuleName().equals("ImplToReqRule")){
				implementationRequirementMatchesMap.put(m, protocol.matchToInt(m));
			}
			// Hack continues here
			if(m.getRuleName().equals("ReqContainerToImplContainerRule")){
				initHack((Container)m.getTargetMatch().getNodeMappings().get("implContainer"));
			}
			
			if(m.getRuleName().equals("ReqConsumerToComputerRule")){
				addComputerMapping(m.getTargetMatch().getNodeMappings().get("implDevice"), m.getSourceMatch().getNodeMappings().get("reqAgent"));
			}
			if (m.getRuleName().equals("ReqProviderToServerRule")) {
				addServerMapping(m.getTargetMatch().getNodeMappings().get("implDevice"), m.getSourceMatch().getNodeMappings().get("reqAgent"));
			}
			
			// End Hack
		}
		// Hack continues here
		findAllRoutes();
		// End Hack

		Collection<UserDefinedILPConstraint> results = new ArrayList<>();

		results = maxSlotsConstraint(serverMatchesMap, results);
		results = serverSpeedConstraint(serverMatchesMap, results);
		results = computerSpeedConstraint(computerMatchesMap, results);
		results = consumerConnectedToProvider(implementationRequirementMatchesMap, results);

		return results;
	}

	private Collection<UserDefinedILPConstraint> maxSlotsConstraint(Map<CCMatch, Integer> matchesMap,
			Collection<UserDefinedILPConstraint> results) {

		Map<Server, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();

		for (CCMatch m : matchesMap.keySet()) {
			Server s = (Server) m.getTargetMatch().getNodeMappings().get("implDevice");

			HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(s, new HashMap<>());
			coefficients.put(matchesMap.get(m), 1.0);

			idToCoefficientMap.put(s, coefficients);
		}

		for (Server s : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(s), "<=", s.getMaxSlots().doubleValue()));
		}

		return results;
	}

	private Collection<UserDefinedILPConstraint> serverSpeedConstraint(Map<CCMatch, Integer> matchesMap,
			Collection<UserDefinedILPConstraint> results) {
		Map<String, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();
		Map<String, Double> serverSpeedMap = new HashMap<>();

		for (CCMatch m : matchesMap.keySet()) {
			Server s = (Server) m.getTargetMatch().getNodeMappings().get("implDevice");
			Provider p = (Provider) m.getSourceMatch().getNodeMappings().get("reqAgent");
			if (!serverSpeedMap.containsKey(s.getName())) {
				double serverSpeed = 0.0;
				for (Cable c : s.getOutgoing()) {
					serverSpeed += c.getSpeed().doubleValue();
				}
				// In order to simplify things, we assume that the maximum
				// server speed (actually we are talking about throughput)
				// is equal to the sum over the speed of its outgoing cables.
				serverSpeedMap.put(s.getName(), serverSpeed);
			}

			HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(s.getName(), new HashMap<>());
			coefficients.put(matchesMap.get(m), p.getSpeed().doubleValue());

			idToCoefficientMap.put(s.getName(), coefficients);
		}
		for (String serverName : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(serverName), "<=",
					serverSpeedMap.get(serverName)));
		}

		return results;
	}

	private Collection<UserDefinedILPConstraint> consumerConnectedToProvider(Map<CCMatch, Integer> matchesMap,
			Collection<UserDefinedILPConstraint> results) {

		Map<String, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();

		for (CCMatch m : matchesMap.keySet()) {
			Computer computer = (Computer) m.getTargetMatch().getNodeMappings().get("implComputer");
			Server server = (Server) m.getTargetMatch().getNodeMappings().get("implServer");
			Consumer consumer = (Consumer) m.getSourceMatch().getNodeMappings().get("reqConsumer");
			Provider provider = (Provider) m.getSourceMatch().getNodeMappings().get("reqProvider");

			HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(consumer.getName(),
					new HashMap<>());
			double coefficient = 0.0;
			if(paths.get(server).isGoalReachable(computer)){
				coefficient += 0.0;
			}else{
				coefficient -= 1.0;
			}
			coefficients.put(matchesMap.get(m), coefficient);
			//System.out.println("Phys: "+computer.getName()+"&"+server.getName()+" to -> Virt: "+consumer.getName()+"&"+provider.getName()+" -> constraint: "+coefficient);
			//coefficients.put(matchesMap.get(m), coefficient);

			idToCoefficientMap.put(consumer.getName(), coefficients);
		}
		//System.out.println(idToCoefficientMap);

		for (String consumerName : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(consumerName), ">=", 0));
		}

		return results;
	}
	
	private Collection<UserDefinedILPConstraint> computerSpeedConstraint(Map<CCMatch, Integer> matchesMap,
			Collection<UserDefinedILPConstraint> results) {

		Map<String, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();
		Map<String, Double> computerSpeedMap = new HashMap<>();

		for (CCMatch m : matchesMap.keySet()) {
			Computer computer = (Computer) m.getTargetMatch().getNodeMappings().get("implDevice");
			Consumer consumer = (Consumer) m.getSourceMatch().getNodeMappings().get("reqAgent");

			if (!computerSpeedMap.containsKey(computer.getName())) {
				double computerSpeed = 0.0;
				for (Cable c : computer.getIncoming()) {
					computerSpeed += c.getSpeed().doubleValue();
				}
				// System.out.println(computerSpeed);
				// In order to simplify things, we assume that the maximum
				// computer speed (actually we are talking about throughput)
				// is equal to the sum over the speed of its Incoming cables.
				computerSpeedMap.put(computer.getName(), computerSpeed);
			}

			HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(computer.getName(),
					new HashMap<>());
			coefficients.put(matchesMap.get(m), consumer.getSpeed().doubleValue());

			idToCoefficientMap.put(computer.getName(), coefficients);
		}

		for (String computerName : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(computerName), "<=",
					computerSpeedMap.get(computerName)));
		}

		return results;
	}

}

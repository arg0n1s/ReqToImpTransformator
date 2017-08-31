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
	
	// extracting vertices and edges from found matches
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
		Map<CCMatch, Integer> implementationRequirementMatchesMap = new HashMap<>();
		Map<CCMatch, Integer> providerServerMatchesMap = new HashMap<>();
		for (CCMatch m : protocol.getMatches()) {
			System.out.println(m.getRuleName());
			if (m.getRuleName().equals("ImplToReqRule2")){
				implementationRequirementMatchesMap.put(m, protocol.matchToInt(m));
			}
			if (m.getRuleName().equals("ImplToReqRule")){
				providerServerMatchesMap.put(m, protocol.matchToInt(m));
			}
			// Hack continues here
			if(m.getRuleName().equals("ReqContainerToImplContainerRule")){
				initHack((Container)m.getTargetMatch().getNodeMappings().get("implContainer"));
			}
			// End Hack
		}
		// Hack continues here
		findAllRoutes();
		// End Hack

		Collection<UserDefinedILPConstraint> results = new ArrayList<>();

		//results = maxSlotsConstraint(providerServerMatchesMap, results);
		//results = serverSpeedConstraint(implementationRequirementMatchesMap, results);
		//results = computerSpeedConstraint(implementationRequirementMatchesMap, results);
		results = consumerConnectedToProvider(implementationRequirementMatchesMap, results);

		return results;
	}

	private Collection<UserDefinedILPConstraint> maxSlotsConstraint(Map<CCMatch, Integer> matchesMap,
			Collection<UserDefinedILPConstraint> results) {

		Map<Server, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();

		for (CCMatch m : matchesMap.keySet()) {
			Server s = (Server) m.getTargetMatch().getNodeMappings().get("implServer");

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
			Server s = (Server) m.getTargetMatch().getNodeMappings().get("implServer");
			Provider p = (Provider) m.getSourceMatch().getNodeMappings().get("reqProvider");
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

			HashMap<Integer, Double> coefficients1 = idToCoefficientMap.getOrDefault(consumer.getName(),
					new HashMap<>());
			double coefficient = 0.0;
			if(!paths.get(server).isGoalReachable(computer)){
				coefficient = 1.0;
			}
			coefficients1.put(matchesMap.get(m), coefficient);
			System.out.println("Phys: "+computer.getName()+"&"+server.getName()+" to -> Virt: "+consumer.getName()+"&"+provider.getName()+" -> constraint: "+coefficient);
			//coefficients.put(matchesMap.get(m), coefficient);

			idToCoefficientMap.put(consumer.getName(), coefficients1);
		}
		

		for (String agentName : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(agentName), "<=", 0));
			//System.out.println(idToCoefficientMap.get(agentName));
		}
		return results;
	}
	
	private Collection<UserDefinedILPConstraint> computerSpeedConstraint(Map<CCMatch, Integer> matchesMap,
			Collection<UserDefinedILPConstraint> results) {

		Map<String, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();
		Map<String, Double> computerSpeedMap = new HashMap<>();

		for (CCMatch m : matchesMap.keySet()) {
			Computer computer = (Computer) m.getTargetMatch().getNodeMappings().get("implComputer");
			Consumer consumer = (Consumer) m.getSourceMatch().getNodeMappings().get("reqConsumer");

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

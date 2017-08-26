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
	public static LinkedList<EObject> consumers;
	public static LinkedList<EObject> providers;
	
	// An elegant way to say, screw pattern matching we're going to do this our way.
	public static void initHack(Container c, requirements.Container c2){
		computers = new LinkedList<EObject>();
		servers = new LinkedList<EObject>();
		vertices = new LinkedList<EObject>();
		edges = new LinkedList<EObject>();
		consumers = new LinkedList<EObject>();
		providers = new LinkedList<EObject>();
		
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
		
		for(EObject current: c2.eContents()){
			if(current.eClass().getInstanceClassName().contains("Consumer")){
				consumers.add(current);
			}
			else if(current.eClass().getInstanceClassName().contains("Provider")){
				providers.add(current);
			}
		}
	}

	@Override
	public Collection<UserDefinedILPConstraint> getUserDefinedConstraints(ConsistencyCheckPrecedenceGraph protocol) {
		System.out.println("CSP");
		Map<CCMatch, Integer> serverMatchesMap = new HashMap<>();
		Map<CCMatch, Integer> computerMatchesMap = new HashMap<>();
		Map<CCMatch, Integer> initRouterMatchesMap = new HashMap<>();
		for (CCMatch m : protocol.getMatches()) {

			if (m.getRuleName().equals("ReqProviderToServerRule")) {
				serverMatchesMap.put(m, protocol.matchToInt(m));
			} else if (m.getRuleName().equals("ReqConsumerToComputerRule")) {
				computerMatchesMap.put(m, protocol.matchToInt(m));
			}
			// Hack begins here
			if(m.getRuleName().equals("ReqContainerToImplContainerRule")){
				initHack((Container)m.getTargetMatch().getNodeMappings().get("implContainer"), (requirements.Container)m.getSourceMatch().getNodeMappings().get("reqContainer"));
			}
		}

		Collection<UserDefinedILPConstraint> results = new ArrayList<>();

		results = maxSlotsConstraint(serverMatchesMap, results);
		results = serverSpeedConstraint(serverMatchesMap, results);
		results = computerSpeedConstraint(computerMatchesMap, results);
		results = initRouterConstraint(initRouterMatchesMap, results);

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
			// System.out.println(s.getName() + " " + p.getName());
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
		// System.out.println(idToCoefficientMap);
		for (String serverName : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(serverName), "<=",
					serverSpeedMap.get(serverName)));
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

	private Collection<UserDefinedILPConstraint> initRouterConstraint(Map<CCMatch, Integer> matchesMap,
			Collection<UserDefinedILPConstraint> results) {

		Map<Router, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();

		for (CCMatch m : matchesMap.keySet()) {
			Router r = (Router) m.getTargetMatch().getNodeMappings().get("router");
			HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(r, new HashMap<>());
			coefficients.put(matchesMap.get(m), 1.0);

			idToCoefficientMap.put(r, coefficients);
		}

		for (Router r : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(r), "=", 1));
		}

		return results;
	}

}

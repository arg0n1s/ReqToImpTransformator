package ReqToImpTransformator.org.moflon.tie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.moflon.tgg.algorithm.ccutils.UserDefinedILPConstraint;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPConstraintProvider;
import org.moflon.tgg.algorithm.datastructures.ConsistencyCheckPrecedenceGraph;
import org.moflon.tgg.runtime.CCMatch;

import implementation.Cable;
import implementation.Server;
import requirements.Provider;

public class CustomILPConstraintProvider implements UserDefinedILPConstraintProvider {

	@Override
	public Collection<UserDefinedILPConstraint> getUserDefinedConstraints(ConsistencyCheckPrecedenceGraph protocol) {
		Map<CCMatch, Integer> matchesMap = new HashMap<>();
		for(CCMatch m : protocol.getMatches()) {
			if(m.getRuleName().equals("ReqProviderToServerRule")) {
				matchesMap.put(m, protocol.matchToInt(m));
			}
		}
		
		Collection<UserDefinedILPConstraint> results = new ArrayList<>();
		
		results = maxSlotsConstraint(protocol, matchesMap, results);
		results = speedConstraint(protocol, matchesMap, results);
		
		return results;
	}
	
	private Collection<UserDefinedILPConstraint> maxSlotsConstraint(ConsistencyCheckPrecedenceGraph protocol, Map<CCMatch, Integer> matchesMap, Collection<UserDefinedILPConstraint> results) {
		
		Map<Server, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();
		
		for (CCMatch m : matchesMap.keySet()) {
			Server s = (Server)m.getTargetMatch().getNodeMappings().get("implDevice");
			
			HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(s, new HashMap<>());
			coefficients.put(matchesMap.get(m), 1.0);
			
			idToCoefficientMap.put(s, coefficients);
		}
		
		for(Server s : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(s), "<=", s.getMaxSlots().doubleValue()));
		}

		return results;
	}
	
	private Collection<UserDefinedILPConstraint> speedConstraint(ConsistencyCheckPrecedenceGraph protocol, Map<CCMatch, Integer> matchesMap, Collection<UserDefinedILPConstraint> results) {
		Map<String, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();
		Map<String, Double> serverSpeedMap = new HashMap<>();
		
		for (CCMatch m : matchesMap.keySet()) {
			Server s = (Server)m.getTargetMatch().getNodeMappings().get("implDevice");
			Provider p = (Provider)m.getSourceMatch().getNodeMappings().get("reqAgent");
			
			if(!serverSpeedMap.containsKey(s.getName())) {
				double serverSpeed = 0.0;
				for (Cable c : s.getOutgoing()) {
					serverSpeed += c.getSpeed().doubleValue();
				}
				// In order to simplify things, we assume that the maximum server speed (actually we are talking about throughput) 
				// is equals to the sum over the speed of its outgoing cables.
				serverSpeedMap.put(s.getName(), serverSpeed);
			}
			
			HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(s.getName(), new HashMap<>());
			coefficients.put(matchesMap.get(m), p.getSpeed().doubleValue());
			
			idToCoefficientMap.put(s.getName(), coefficients);
		}
		
		for(String serverName : idToCoefficientMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(serverName), "<=", serverSpeedMap.get(serverName)));
		}

		return results;
	}
	
}

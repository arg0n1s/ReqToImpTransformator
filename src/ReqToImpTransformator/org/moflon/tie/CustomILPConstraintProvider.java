package ReqToImpTransformator.org.moflon.tie;

import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.moflon.tgg.algorithm.ccutils.UserDefinedILPConstraint;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPConstraintProvider;
import org.moflon.tgg.algorithm.datastructures.ConsistencyCheckPrecedenceGraph;
import org.moflon.tgg.runtime.CCMatch;

import implementation.Server;

public class CustomILPConstraintProvider implements UserDefinedILPConstraintProvider {

	@Override
	public Collection<UserDefinedILPConstraint> getUserDefinedConstraints(ConsistencyCheckPrecedenceGraph protocol) {
		Map<Server, Collection<Integer>> serverIdMap = new HashMap<>();
		Map<String, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();
		//Map<String, HashMap<Integer, Double>> idToCoefficientMap_MTBF = new HashMap<>();
		double meanTimeBetweenFailures = 0.0;
		for(CCMatch m : protocol.getMatches()) {
			if(m.getRuleName().equals("ReqProviderToServerRule")) {
				Server s = (Server)m.getTargetMatch().getNodeMappings().get("implDevice");
				Collection<Integer> ids = serverIdMap.getOrDefault(s.getName(), new ArrayList<>());
				ids.add(protocol.matchToInt(m));
				serverIdMap.put(s, ids);
				
				HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(s.getName(), new HashMap<>());
				//HashMap<Integer, Double> coefficients_MTBF = idToCoefficientMap_MTBF.getOrDefault(s.getName(), new HashMap<>());
				
				meanTimeBetweenFailures = s.getMTBF().doubleValue();
				System.out.println(meanTimeBetweenFailures);
				coefficients.put(protocol.matchToInt(m), 1.0);
				//coefficients_MTBF.put(protocol.matchToInt(m), meanTimeBetweenFailures);
				
				idToCoefficientMap.put(s.getName(), coefficients);
				//idToCoefficientMap_MTBF.put(s.getName(), coefficients_MTBF);
				
			}
		}
		
		Collection<UserDefinedILPConstraint> results = new ArrayList<>();
		for(Server s : serverIdMap.keySet()) {
			//results.add(new UserDefinedILPConstraint(idToCoefficientMap_MTBF.get(s.getName()), "<=", 400.0));
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(s.getName()), "<=", s.getMaxSlots().doubleValue()));
		}
		return results;
	}
	

}

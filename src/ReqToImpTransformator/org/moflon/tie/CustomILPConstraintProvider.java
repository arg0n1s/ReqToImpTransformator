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
		Map<String, Collection<Integer>> serverIdMap = new HashMap<>();
		Map<String, HashMap<Integer, Double>> idToCoefficientMap = new HashMap<>();
		for(CCMatch m : protocol.getMatches()) {
			if(m.getRuleName().equals("ReqProviderToServerRule")) {
				Server s = (Server)m.getTargetMatch().getNodeMappings().get("implDevice");
				Collection<Integer> ids = serverIdMap.getOrDefault(s.getName(), new ArrayList<>());
				ids.add(protocol.matchToInt(m));
				serverIdMap.put(s.getName(), ids);
				
				HashMap<Integer, Double> coefficients = idToCoefficientMap.getOrDefault(s.getName(), new HashMap<>());
				coefficients.put(protocol.matchToInt(m), 1.0);
				idToCoefficientMap.put(s.getName(), coefficients);
				
			}
		}
		
		Collection<UserDefinedILPConstraint> results = new ArrayList<>();
		for(String serverName : serverIdMap.keySet()) {
			results.add(new UserDefinedILPConstraint(idToCoefficientMap.get(serverName), "<=", 1));
		}
		return results;
	}
	

}

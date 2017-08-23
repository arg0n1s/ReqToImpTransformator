package ReqToImpTransformator.org.moflon.tie;

import java.util.HashMap;
import java.util.Map;

import org.moflon.tgg.algorithm.ccutils.UserDefinedILPObjective;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPObjectiveProvider;
import org.moflon.tgg.algorithm.datastructures.ConsistencyCheckPrecedenceGraph;
import org.moflon.tgg.runtime.CCMatch;

import implementation.Server;

public class CustomILPObjectiveProvider implements UserDefinedILPObjectiveProvider {
	public UserDefinedILPObjective getUserDefinedObjective(ConsistencyCheckPrecedenceGraph protocol) {
		Map<Integer, Double> idsToCoefficients = new HashMap<>();
	
		
		for (int matchId : protocol.getMatchIDs().toArray()) {
			CCMatch match = protocol.intToMatch(matchId);
			Double weight;
			if(match.getRuleName().equals("ReqProviderToServerRule")){
				Server s = (Server)match.getTargetMatch().getNodeMappings().get("implDevice");
				weight = s.getMTBF().doubleValue();
			}
			
			else if(match.getRuleName().equals("VirtualNodeToRouterRule2")||
					match.getRuleName().equals("VirtualNodeToComputerRule")||
					match.getRuleName().equals("VirtualNodeToServerRule")){
				//if(match.getRuleName().equals("VirtualNodeToComputerRule")) {
				//	System.out.println("Hola");
				//}
				weight = (double) match.getSourceMatch().getCreatedHashSet().size();
				weight += match.getTargetMatch().getCreatedHashSet().size();
				weight *= -0.0001;
			}
			
			else{
				weight = (double) match.getSourceMatch().getCreatedHashSet().size();
				weight += match.getTargetMatch().getCreatedHashSet().size();
			}
			idsToCoefficients.put(matchId, weight);
		}
		return new UserDefinedILPObjective(idsToCoefficients, UserDefinedILPObjective.OptGoal.MAX);
	}
}
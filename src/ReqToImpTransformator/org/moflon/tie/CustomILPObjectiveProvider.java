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
			Double weight = 0.0;
			
			/*if(match.getRuleName().equals("ReqProviderToServerRule")){
				Server s = (Server)match.getTargetMatch().getNodeMappings().get("implDevice");
				weight = s.getMTBF().doubleValue();
			}*/
			
			if(match.getRuleName().equals("VirtualNodeToRouterRule2")||
					match.getRuleName().equals("VirtualNodeToComputerRule")|| 
					match.getRuleName().equals("VirtualNodeToRouterRule1") ||
					match.getRuleName().equals("VirtualNodeToServerRule")){
				//weight = (double) match.getSourceMatch().getCreatedHashSet().size();
				//weight = (double) match.getCreatedHashSet().size();
				weight = (double) match.getSourceMatch().getCreatedHashSet().size();
				weight += match.getTargetMatch().getCreatedHashSet().size();
				System.out.println(weight);
				//weight += match.getTargetMatch().getCreatedHashSet().size();
				weight *= -0.0001;
			}
			
			else{
				weight = (double) match.getSourceMatch().getCreatedHashSet().size();
				weight += match.getTargetMatch().getCreatedHashSet().size();
				//weight = (double) match.getCreatedHashSet().size();
				//System.out.println(weight);
				//System.out.println((double) match.getCreatedHashSet().size());
			}
			idsToCoefficients.put(matchId, weight);
			
			if(match.getRuleName().equals("VirtualNodeToRouterRule2")) {
				System.out.println("Hola");
			}
		}
		System.out.println(idsToCoefficients);
		return new UserDefinedILPObjective(idsToCoefficients, UserDefinedILPObjective.OptGoal.MAX);
	}
}
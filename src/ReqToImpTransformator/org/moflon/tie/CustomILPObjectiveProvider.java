package ReqToImpTransformator.org.moflon.tie;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPObjective;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPObjectiveProvider;
import org.moflon.tgg.algorithm.datastructures.ConsistencyCheckPrecedenceGraph;
import org.moflon.tgg.runtime.CCMatch;

import implementation.Computer;
import implementation.Server;
import requirements.Consumer;
import requirements.Provider;

public class CustomILPObjectiveProvider implements UserDefinedILPObjectiveProvider {
	
	public UserDefinedILPObjective getUserDefinedObjective(ConsistencyCheckPrecedenceGraph protocol) {
		//System.out.println("Objective Function");
		Map<Integer, Double> idsToCoefficients = new HashMap<>();
		
		for (int matchId : protocol.getMatchIDs().toArray()) {
			CCMatch match = protocol.intToMatch(matchId);
			Double weight = 1.0;
			
			if(match.getRuleName().equals("ImplToReqRule")){
				Server s = (Server)match.getTargetMatch().getNodeMappings().get("implServer");
				weight = s.getMTBF().doubleValue();
				
			}else if(match.getRuleName().equals("ImplToReqRule2")){
				Computer c = (Computer)match.getTargetMatch().getNodeMappings().get("implComputer");
				Server s = (Server)match.getTargetMatch().getNodeMappings().get("implServer");
				if(CustomILPConstraintProvider.paths.get(s).isGoalReachable(c)){
					weight = 1.0 + s.getMTBF().doubleValue()/(1.0+CustomILPConstraintProvider.paths.get(s).getDistanceToGoal(c));
				}
			}
			
			idsToCoefficients.put(matchId, weight);
			//System.out.println(idsToCoefficients);
		}
		//System.out.println(idsToCoefficients);
		
		return new UserDefinedILPObjective(idsToCoefficients, UserDefinedILPObjective.OptGoal.MAX);
	}
	
}


package ReqToImpTransformator.org.moflon.tie;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPObjective;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPObjectiveProvider;
import org.moflon.tgg.algorithm.datastructures.ConsistencyCheckPrecedenceGraph;
import org.moflon.tgg.runtime.CCMatch;

import implementation.Server;

public class CustomILPObjectiveProvider implements UserDefinedILPObjectiveProvider {
	
	public UserDefinedILPObjective getUserDefinedObjective(ConsistencyCheckPrecedenceGraph protocol) {
		System.out.println("Objective Function");
		Map<Integer, Double> idsToCoefficients = new HashMap<>();
		
		for (int matchId : protocol.getMatchIDs().toArray()) {
			CCMatch match = protocol.intToMatch(matchId);
			Double weight = 0.0;
			
			if(match.getRuleName().equals("ReqProviderToServerRule")){
				Server s = (Server)match.getTargetMatch().getNodeMappings().get("implDevice");
				weight = s.getMTBF().doubleValue();
				FindShortestPath fp = new FindShortestPath();
				fp.findShortestRoutes(match.getTargetMatch().getNodeMappings().get("implDevice"));
				double sumOfDist = 1.0;
				int numOfReachablePcs = 1;
				for(EObject computer:CustomILPConstraintProvider.computers){
					if(fp.getMinTree().containsKey(computer)){
						sumOfDist+=fp.getDistanceToGoal(computer);
						numOfReachablePcs++;
						System.out.println("Route: "+fp.getRouteFromGoal(s, computer)+" // Cost: "+fp.getDistanceToGoal(computer));
					}else{
						sumOfDist+=fp.getMinTree().size()*2.0;
						numOfReachablePcs++;
					}
					
				}
				sumOfDist /=numOfReachablePcs;
				weight -=sumOfDist;
				
				//System.out.println(findShortestRoutes(match.getTargetMatch().getNodeMappings().get("implDevice")));
			}
			/*
			else if(match.getRuleName().equals("VirtualNodeToRouterRule2")||
					match.getRuleName().equals("VirtualNodeToRouterRule1")||
					match.getRuleName().equals("VirtualNodeToComputerRule")||
					match.getRuleName().equals("VirtualNodeToServerRule")){
				
				//weight = (double) match.getSourceMatch().getCreatedHashSet().size();
				weight = (double) match.getCreatedHashSet().size();
				//weight += match.getTargetMatch().getCreatedHashSet().size();
				weight *= -0.001;
			}
			*/
			else{
				//weight = (double) match.getSourceMatch().getCreatedHashSet().size();
				//weight += match.getTargetMatch().getCreatedHashSet().size();
				weight = (double) match.getCreatedHashSet().size();
				//System.out.println(weight);
				//System.out.println((double) match.getCreatedHashSet().size());
			}
			idsToCoefficients.put(matchId, weight);
			//System.out.println(idsToCoefficients);
		}
		return new UserDefinedILPObjective(idsToCoefficients, UserDefinedILPObjective.OptGoal.MAX);
	}
	
}


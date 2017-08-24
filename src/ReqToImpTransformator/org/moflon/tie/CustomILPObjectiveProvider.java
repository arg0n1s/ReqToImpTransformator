package ReqToImpTransformator.org.moflon.tie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPObjective;
import org.moflon.tgg.algorithm.ccutils.UserDefinedILPObjectiveProvider;
import org.moflon.tgg.algorithm.datastructures.ConsistencyCheckPrecedenceGraph;
import org.moflon.tgg.runtime.CCMatch;

import implementation.Cable;
import implementation.Computer;
import implementation.Container;
import implementation.Device;
import implementation.Server;

public class CustomILPObjectiveProvider implements UserDefinedILPObjectiveProvider {
	HashMap<EObject, Double> vertices;
	HashMap<EObject, Double> edges;
	LinkedList<EObject> computers;
	
	public UserDefinedILPObjective getUserDefinedObjective(ConsistencyCheckPrecedenceGraph protocol) {
		Map<Integer, Double> idsToCoefficients = new HashMap<>();
		Container c = null;
		for (int matchId : protocol.getMatchIDs().toArray()){
			CCMatch match = protocol.intToMatch(matchId);
			if(match.getRuleName().equals("ReqContainerToImplContainerRule")){
				c = (Container)match.getTargetMatch().getNodeMappings().get("implContainer");
				
			}
		}
		
		vertices = new HashMap<EObject, Double>();
		edges = new HashMap<EObject, Double>();
		computers = new LinkedList<EObject>();
		
		for(EObject current: c.eContents()){
			if(current.eClass().getInstanceClassName().contains("Cable")){
				edges.put(current, 1.0);
			}else{
				vertices.put(current, -1.0);
			}
			if(current.eClass().getInstanceClassName().contains("Computer")){
				computers.add(current);
			}
		}
		
		for (int matchId : protocol.getMatchIDs().toArray()) {
			CCMatch match = protocol.intToMatch(matchId);
			Double weight = 0.0;
			//System.out.println(match.getRuleName());
			//System.out.println(match.getCreatedHashSet().size());
			
			if(match.getRuleName().equals("ReqProviderToServerRule")){
				Server s = (Server)match.getTargetMatch().getNodeMappings().get("implDevice");
				weight = s.getMTBF().doubleValue();
				LinkedList<EObject> route = findShortestRoutes(match.getTargetMatch().getNodeMappings().get("implDevice"));
				//System.out.println("Minimal Tree: "+route+" //Hops to last Node:" + vertices.get(route.getLast()));
				double sumOfDist = 1.0;
				int numOfReachablePcs = 1;
				for(EObject computer:computers){
					if(route.contains(computer)){
						sumOfDist+=vertices.get(computer);
						numOfReachablePcs++;
					}else{
						sumOfDist+=route.size()*2.0;
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
	
	public LinkedList<EObject> findShortestRoutes(EObject start) {
		
		
		// sicherstellen, dass die Distanz aller Knoten auf -1 gestellt ist
		for (EObject current : vertices.keySet()) {
			if(!current.equals(start)){
				vertices.replace(current, -1.0);
			}else{
				// Startbedinungen herstellen
				vertices.replace(current, 0.0);
			}
		}
		LinkedList<EObject> route = new LinkedList<EObject>();
		LinkedList<EObject> queue = new LinkedList<EObject>();
		// Queue mit den Knoten initialisieren
		for (EObject current : vertices.keySet()) {
			queue.add(current);
		}
		// ueber die Queue iterieren dabei jeweils extractMin anwenden und
		// relax auf die Adjazenzliste des Elementes anwenden.
		while (queue.size() != 0) {
			//System.out.println(queue.size());
			EObject u = extractMinDistance(queue);
			route.add(u);
			for (EObject current : getAdjacents(u)) {
				relaxDistance(u, current);
			}
			int counter = 0;
			for(EObject current : queue){
				if(getRelativeDistance(current) < 0){
					counter ++;
				}
			}
			if(counter == queue.size()){
				queue.clear();
			}
		}
		
		return route;
	}
	
	public LinkedList<EObject> getAdjacents(EObject u){
		LinkedList<EObject> adjacents = new LinkedList<EObject>();
		Device d = (Device)u;
		for(Cable current: d.getOutgoing()){
			adjacents.push(current.getTarget());
		}
		return adjacents;
	}
	
	public EObject extractMinDistance(LinkedList<EObject> queue){
		EObject minItem = queue.getFirst();
		for (EObject current : queue) {
			// der relative Abstand des naechsten Element
			double cDist = getRelativeDistance(current);
			// der relative Abstand des vorherigen Element
			double fDist = getRelativeDistance(minItem);
			if ((fDist == -1) && (cDist >= 0)) {
				// ist die Zeit des vorherigen Elementes negativ und
				// die des naechsten positiv, so wird das naechste Element zum
				// Vorgaenger.
				minItem = current;
			} else if (cDist <= fDist && (cDist != -1)) {
				// Ist das naechste Element kleiner als das vorherige und
				// nicht negativ, so wird naechste Element zum Vorgaenger.
				minItem = current;
			}
		}
		queue.remove(minItem);
		
		return minItem;
	}
	
	public double getRelativeDistance(EObject u){
		return vertices.get(u);
	}
	
	public void setRelativeDistance(EObject u, double value){
		vertices.replace(u, value);
	}
	
	public void relaxDistance(EObject source, EObject target) {
		double distSourceToTarget = getDistanceBetweenVertices(source, target);

		if ((getRelativeDistance(target) > getRelativeDistance(source)
				+ distSourceToTarget)
				|| (getRelativeDistance(target) == -1)) {
			// ist eine kuerzere Strecke gefunden worden, so wird der Vorgaenger
			// entsprechend geaendert
			// und die gesamte durchquerte Strecke erneuert.
			setRelativeDistance(target,getRelativeDistance(source) + distSourceToTarget);
			//target.setPredecessor(source);
		}
	}
	
	public double getDistanceBetweenVertices(EObject source, EObject target){
		Device s = (Device)source;
		double distance = -1.0;
		for(Cable current: s.getOutgoing()){
			if(current.getTarget().getName().equals(((Device)target).getName())){
				distance = 1.0;
			}
		}
		return distance;
		
	}

	
	
}


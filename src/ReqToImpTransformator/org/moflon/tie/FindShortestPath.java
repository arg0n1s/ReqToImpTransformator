package ReqToImpTransformator.org.moflon.tie;

import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.emf.ecore.EObject;

import implementation.Cable;
import implementation.Device;
import javafx.util.Pair;

public class FindShortestPath {
	HashMap<EObject, Pair<Double, EObject>> vertices;
	HashMap<EObject, Double> edges;
	HashMap<EObject, Pair<Double, EObject>> minimalSpanningTree;
	
	public FindShortestPath(){
		vertices = new HashMap<EObject, Pair<Double, EObject>>();
		edges = new HashMap<EObject, Double>();
		
		for(EObject vertex : CustomILPConstraintProvider.vertices){
			vertices.put(vertex, new Pair<Double, EObject>(-1.0,null));
		}
		
		for(EObject edge : CustomILPConstraintProvider.edges){
			edges.put(edge, 1.0);
		}
	}
	
	public boolean isGoalReachable(EObject goal){
		return minimalSpanningTree.containsKey(goal);
	}
	
	public double getDistanceToGoal(EObject goal){
		return minimalSpanningTree.get(goal).getKey();
	}
	
	public LinkedList<EObject> getRouteFromGoal(EObject start, EObject goal){
		LinkedList<EObject> route = new LinkedList<EObject>();
		EObject current = goal;
		route.add(goal);
		while(!minimalSpanningTree.get(current).getValue().equals(start)){
			current = minimalSpanningTree.get(current).getValue();
			route.add(current);
		}
		route.add(start);
		
		return route;
	}
	
	public HashMap<EObject, Pair<Double, EObject>> getMinTree(){
		return minimalSpanningTree;
	}

	public void findShortestRoutes(EObject start) {

		// sicherstellen, dass die Distanz aller Knoten auf -1 gestellt ist
		for (EObject current : vertices.keySet()) {
			if (!current.equals(start)) {
				vertices.replace(current, new Pair<Double, EObject>(-1.0, null));
			} else {
				// Startbedinungen herstellen
				vertices.replace(current, new Pair<Double, EObject>(0.0, null));
			}
		}
		LinkedList<EObject> minTree = new LinkedList<EObject>();
		LinkedList<EObject> queue = new LinkedList<EObject>();
		// Queue mit den Knoten initialisieren
		for (EObject current : vertices.keySet()) {
			queue.add(current);
		}
		// ueber die Queue iterieren dabei jeweils extractMin anwenden und
		// relax auf die Adjazenzliste des Elementes anwenden.
		while (queue.size() != 0) {
			// System.out.println(queue.size());
			EObject u = extractMinDistance(queue);
			minTree.add(u);
			for (EObject current : getAdjacents(u)) {
				relaxDistance(u, current);
			}
			int counter = 0;
			for (EObject current : queue) {
				if (getRelativeDistance(current) < 0) {
					counter++;
				}
			}
			if (counter == queue.size()) {
				queue.clear();
			}
		}
		minimalSpanningTree = new HashMap<EObject, Pair<Double, EObject>>(minTree.size());
		for(EObject current : minTree){
			minimalSpanningTree.put(current, vertices.get(current));
		}

	}

	private LinkedList<EObject> getAdjacents(EObject u) {
		LinkedList<EObject> adjacents = new LinkedList<EObject>();
		Device d = (Device) u;
		for (Cable current : d.getOutgoing()) {
			adjacents.push(current.getTarget());
		}
		return adjacents;
	}

	private EObject extractMinDistance(LinkedList<EObject> queue) {
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

	private double getRelativeDistance(EObject u) {
		return vertices.get(u).getKey();
	}

	private void setRelativeDistance(EObject u, double value) {
		Pair<Double, EObject> entry = new Pair<Double, EObject>(value, vertices.get(u).getValue());
		vertices.replace(u, entry);
	}
	
	private void setPredecessor(EObject current, EObject pred){
		Pair<Double, EObject> entry = new Pair<Double, EObject>(vertices.get(current).getKey(), pred);
		vertices.replace(current, entry);
	}

	private void relaxDistance(EObject source, EObject target) {
		double distSourceToTarget = getDistanceBetweenVertices(source, target);

		if ((getRelativeDistance(target) > getRelativeDistance(source) + distSourceToTarget)
				|| (getRelativeDistance(target) == -1)) {
			// ist eine kuerzere Strecke gefunden worden, so wird der Vorgaenger
			// entsprechend geaendert
			// und die gesamte durchquerte Strecke erneuert.
			setRelativeDistance(target, getRelativeDistance(source) + distSourceToTarget);
			setPredecessor(target, source);
			// target.setPredecessor(source);
		}
	}

	private double getDistanceBetweenVertices(EObject source, EObject target) {
		Device s = (Device) source;
		double distance = -1.0;
		for (Cable current : s.getOutgoing()) {
			if (current.getTarget().getName().equals(((Device) target).getName())) {
				distance = edges.get(current);
			}
		}
		return distance;

	}

}

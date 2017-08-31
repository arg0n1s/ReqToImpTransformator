package ReqToImpTransformator.org.moflon.tie;

import java.io.IOException;
import org.apache.log4j.BasicConfigurator;
import org.moflon.tgg.algorithm.synchronization.SynchronizationHelper;


import ReqToImpTransformator.ReqToImpTransformatorPackage;


public class ReqToImpTransformatorConsistencyCheck extends SynchronizationHelper{

   public ReqToImpTransformatorConsistencyCheck()
   {
      super(ReqToImpTransformatorPackage.eINSTANCE, ".");
   }
	public static void main(String[] args) throws IOException {
		// Set up logging
        BasicConfigurator.configure();
        
        ReqToImpTransformatorConsistencyCheck helper = new ReqToImpTransformatorConsistencyCheck();
        helper.loadSrc("instances/src.xmi");
		helper.loadTrg("instances/trg.xmi");

		boolean prepareDeltas = true;
		helper.setUserDefiendILPConstraintProvider(new CustomILPConstraintProvider());
		helper.setUserDefiendILPObjectiveProvider(new CustomILPObjectiveProvider());
		helper.createCorrespondences(prepareDeltas);
		System.out.println(helper.corr.getCorrespondences().size());
		
		if(prepareDeltas){
			//src and trg models are modified when preparing deltas.
			//save all files in a separate location
			helper.saveSrc("instances/cc_result/src.xmi");
			helper.saveTrg("instances/cc_result/trg.xmi");
			helper.saveCorr("instances/cc_result/corr.xmi");
			helper.saveConsistencyCheckProtocol("instances/cc_result/protocol.xmi");
			helper.saveInconsistentSourceDelta("instances/cc_result/src.delta.xmi");
			helper.saveInconsistentTargetDelta("instances/cc_result/trg.delta.xmi");
		}
		else{
			//src and trg models are not modified.
			//save correspondence model and protocol only
			helper.saveCorr("instances/corr.xmi");
			helper.saveConsistencyCheckProtocol("instances/protocol.xmi");
		}
	}
}
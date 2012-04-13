package energy.interproc;


import com.ibm.wala.dataflow.graph.AbstractMeetOperator;
import com.ibm.wala.dataflow.graph.BasicFramework;
import com.ibm.wala.dataflow.graph.BooleanFalse;
import com.ibm.wala.dataflow.graph.BooleanIdentity;
import com.ibm.wala.dataflow.graph.BooleanIntersection;
import com.ibm.wala.dataflow.graph.BooleanSolver;
import com.ibm.wala.dataflow.graph.BooleanTrue;
import com.ibm.wala.dataflow.graph.BooleanUnion;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.fixpoint.BooleanVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;

public class CtxInsensLocking {


   /**
    * Run the solver either for acquire (true) or for Release (false)
    */
    private boolean behavior = true; 
  
    /**
     * the exploded interprocedural control-flow graph on which to compute the analysis
     */
    private final ExplodedInterproceduralCFG icfg;

    private boolean mayOrMust = true;

    /**
     * TODO: figure out what needs to be mapped here 
     * maps each state static field to the numbers of the statements (in {@link #putInstrNumbering}) that define it; used for kills in flow
     * functions
     */
    /*
    private final Map<String, BooleanVariable> state2DefStatements = HashMapFactory.make();
    */
    private static final boolean VERBOSE = false;

    public CtxInsensLocking(ExplodedInterproceduralCFG icfg) {
      this.icfg = icfg;
      //numberStates();
    }

    /*
     * TODO: Probably need to create new state for every wakelock variable -> BitVectors
     */
    /*
    private OrdinalSetMapping<String> numberStates() {
      ArrayList<String> lockStates = new ArrayList<String>();
      String lockedState  = "held";
      BooleanVariable bvTrue = new BooleanVariable(true);      
      state2DefStatements.put(lockedState, bvTrue);
      
      lockStates.add(lockedState);
      
      BooleanVariable bvFalse = new BooleanVariable(false);
      String unlockedState  = "unheld";
      state2DefStatements.put(unlockedState, bvFalse);
      lockStates.add(unlockedState);
      
      return new ObjectArrayMapping<String>(lockStates.toArray(new String[lockStates.size()]));
    }
    */
    
    private class TransferFunctions implements 
      ITransferFunctionProvider<BasicBlockInContext<IExplodedBasicBlock>, BooleanVariable> {
      /**
       * meet operation: keep the locked state (logical "or")  
       */
      public AbstractMeetOperator<BooleanVariable> getMeetOperator() {
        
        if (mayOrMust)
          return BooleanUnion.instance();
        else
          return BooleanIntersection.instance();
          
      }
      
      public UnaryOperator<BooleanVariable> getNodeTransferFunction(
          BasicBlockInContext<IExplodedBasicBlock> node) {
        IExplodedBasicBlock ebb = node.getDelegate();
        SSAInstruction instruction = ebb.getInstruction();
//        int instructionIndex = ebb.getFirstInstructionIndex();
//        CGNode cgNode = node.getNode();        
        
        if (instruction instanceof SSAInvokeInstruction) {
          final SSAInvokeInstruction invInstr = (SSAInvokeInstruction) instruction;
          String methSig = invInstr.getDeclaredTarget().getSignature().toString();
          
          
          if (behavior) {
            if (methSig.equals("android.os.PowerManager$WakeLock.acquire()V"))            
              return BooleanTrue.instance();
            else if (methSig.equals("android.os.PowerManager$WakeLock.release()V")) 
              return BooleanFalse.instance();
            else 
              return BooleanIdentity.instance();
          }
          else {            
              if (methSig.equals("android.os.PowerManager$WakeLock.acquire()V"))            
                return BooleanFalse.instance();
              else if (methSig.equals("android.os.PowerManager$WakeLock.release()V")) 
                return BooleanTrue.instance();
              else 
                return BooleanIdentity.instance();
            }            
        }
        return BooleanIdentity.instance();
      }

      /**
       * here we need an edge transfer function for call-to-return edges (see
       * {@link #getEdgeTransferFunction(BasicBlockInContext, BasicBlockInContext)})
       */
      public boolean hasEdgeTransferFunctions() {
        return true;
      }

      public boolean hasNodeTransferFunctions() {
        return true;
      }

      /**
       * for direct call-to-return edges at a call site, the edge transfer function will kill all facts, since we only want to
       * consider facts that arise from going through the callee
       */
      public UnaryOperator<BooleanVariable> getEdgeTransferFunction(BasicBlockInContext<IExplodedBasicBlock> src,
          BasicBlockInContext<IExplodedBasicBlock> dst) {
        if (isCallToReturnEdge(src, dst)) {
          return BooleanIdentity.instance();
        } else {
          return BooleanIdentity.instance();
        }
      }

      private boolean isCallToReturnEdge(BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dst) {
        SSAInstruction srcInst = src.getDelegate().getInstruction();
        return srcInst instanceof SSAAbstractInvokeInstruction && src.getNode().equals(dst.getNode());
      }


    }

    /**
     * Run the analysis
     * @param mayOrMust : is this a may analysis (true) or a must (false)
     * @param lock : change the behavior lock -> unlock or unlock -> lock 
     * @return
     */
    public BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>> analyze(boolean mayOrMust, boolean lock) {
      
      /* Not the best way to do this */
      behavior = lock;
      this.mayOrMust  = mayOrMust;
      
      BasicFramework<BasicBlockInContext<IExplodedBasicBlock>, BooleanVariable> framework = 
          new BasicFramework<BasicBlockInContext<IExplodedBasicBlock>, BooleanVariable>(icfg, new TransferFunctions());
      
      BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>> solver =
          new BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>>(framework);
      
      try {
        solver.solve(null);
      } catch (CancelException e) {
        // this shouldn't happen
        assert false;
      }
      if (VERBOSE) {
        for (BasicBlockInContext<IExplodedBasicBlock> ebb : icfg) {
          System.out.println(ebb);
          System.out.println(ebb.getDelegate().getInstruction());
          System.out.println(solver.getIn(ebb));
          System.out.println(solver.getOut(ebb));
        }
      }
      return solver;
    }



}

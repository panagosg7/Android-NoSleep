package edu.ucsd.energy.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

public class IntentCalls extends AbstractManager<IntentInstance>{
	
	private static int DEBUG = 1;
	
	public IntentCalls(AppCallGraph cg) {
		super(cg);
	}

	String[] interestingStrings = {"setFlags(I)Landroid/content/Intent;",
			"setAction(Ljava/lang/String;)Landroid/content/Intent;",
			"setType(Ljava/lang/String;)Landroid/content/Intent;",
			"setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;"};
	
	Set interestingMethods = new HashSet(Arrays.asList(interestingStrings));
	
	private void registerIntent(SSAInstruction instruction, IntentInstance intent) {
		// TODO Auto-generated method stub
		
	}

	private HashSet<SSAProgramPoint> scanNewIntents() {
		HashSet<SSAProgramPoint> newInsts = new HashSet<SSAProgramPoint>();
		for (Iterator<NewSiteReference> it = ir.iterateNewSites(); //ir.iterateAllInstructions(); 
				it.hasNext();) {				
			SSANewInstruction newi = ir.getNew(it.next());
			if (newi.getConcreteType().toString().equals("<Application,Landroid/content/Intent>")) {
				SSAProgramPoint pp = new SSAProgramPoint(node, newi);
				newInsts.add(pp);
			}
		}	
		return newInsts;
	}
	
	
	boolean printedST = false;
	
	/**
	 * Find the real def of a ssa variable. Will accept one value 
	 * if a phi is encountered.
	 * @param du 
	 */
	private Collection<SSAInstruction> getDefFromVarInt(int ssaVar) {
		SymbolTable symbolTable = ir.getSymbolTable();
		if (symbolTable != null) {
			
			E.log(1, "\tLooking for: " + ssaVar);
			/*
			if (value != null) {
				E.log(1, value.toString());
			}
			 */
			if(symbolTable.isConstant(ssaVar)) {
				
				E.log(1, "it's a constant");
				return null;
			}
			
			SSAInstruction def = du.getDef(ssaVar);
			getComponentNameFromInstruction(def);
			
		//	E.log(1, ir.toString());
			printedST = true;
		}
		
		return null;
		
	}
	
	
	private HashSet<SSAInstruction> getComponentNameFromInstruction(SSAInstruction instr) {
		if (instr instanceof SSANewInstruction) {
			SSANewInstruction newi = (SSANewInstruction) instr;
			String atom = newi.getConcreteType().toString();
			//E.log(1, atom);
			if (atom.equals(new String("<Application,Landroid/content/ComponentName>"))) {
				int def = newi.getDef();
				
				for(Iterator<SSAInstruction> it = du.getUses(def); it.hasNext(); ) {
					SSAInstruction next = it.next();
					if (next instanceof SSAInvokeInstruction) {
						SSAInvokeInstruction inv = (SSAInvokeInstruction) next;
						String invName = inv.getDeclaredTarget().getSelector().toString();
						//E.log(1, invName);
						if (invName.equals(new String("<init>(Ljava/lang/String;Ljava/lang/String;)V"))) {							
							int use1 = inv.getUse(1);	//Package of the component
							int use2 = inv.getUse(2);	//Name of class							
							SymbolTable symbolTable = ir.getSymbolTable();
							if(symbolTable.isConstant(use1) && symbolTable.isConstant(use2)) {
								E.log(1, "Got the name(??): " + symbolTable.getConstantValue(use1) + 
										" , " + symbolTable.getConstantValue(use1)); 
							}
						}
						else{
							//E.log(1, "Cannot resolve.");
						}
					}
				}
				
			}
			
		}
		
		if (instr instanceof SSAPhiInstruction) {
			SSAPhiInstruction phi = (SSAPhiInstruction) instr;
			HashSet<SSAInstruction> ret = new HashSet<SSAInstruction>();
			for(int i = 0; i < phi.getNumberOfUses(); i++) {
				int use = phi.getUse(i);
				Collection<SSAInstruction> defs = getDefFromVarInt(use);
				if (defs != null) {
					ret.addAll(defs);				
				}
			}
			return ret;			
		}
		return null;		
	}
	
	

	public IntentInstance newInstance() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isInterestingType(TypeReference typeReference) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void dumpInfo() {
		// TODO Auto-generated method stub
		
	}

	@Override
	boolean isInterestingMethod(MethodReference declaredTarget) {
		String selector = declaredTarget.getSelector().toString();
		return interestingMethods.contains(selector);
	}

	@Override
	void visitInvokeInstruction(SSAInvokeInstruction instruction) {
		// TODO Auto-generated method stub
		
	}


	@Override
	IntentInstance newInstance(SSAProgramPoint pp) {
		return new IntentInstance(pp);
	}

	@Override
	IntentInstance newInstance(FieldReference field) {
		return new IntentInstance(field);
	}

	@Override
	boolean isNewInstruction(SSAInstruction instr) {
		if (instr instanceof SSANewInstruction) {
			SSANewInstruction newi = (SSANewInstruction) instr;
			return newi.getConcreteType().toString().
					equals("<Application,Landroid/content/Intent>");
		}
		return false;
	}

}

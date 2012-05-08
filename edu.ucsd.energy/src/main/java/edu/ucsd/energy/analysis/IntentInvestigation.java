package edu.ucsd.energy.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.ssa.Value;

import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

public class IntentInvestigation {
	
	private AppCallGraph cg;
	private ComponentManager cm; 
	
	public IntentInvestigation(ComponentManager componentManager) {
		this.cg = componentManager.getCG();
		this.cm = componentManager;
	}
	
	
	/**
	 * _Context-insensitive_ notion of an intent
	 * @author pvekris
	 *
	 */
	public class Intent {
		private SSAProgramPoint startIntent;
		private SSANewInstruction newInstr;
		
		private String componentName;
		
		Intent(SSANewInstruction create) {
			this.newInstr = create;
			uses = new ArrayList<String>();
		}

		private ArrayList<String> uses;
		
		
		//TODO: put extras...
		
		public String toString() {
			StringBuffer sb = new StringBuffer();
			//sb.append("Created: " + newInstr.toString() + "\n");
			sb.append("SSAVar:  " + getSSAVar() + "\n");
			
			for (String i : uses) {
				sb.append("\tUse: " + i + "\n");
			}
			return sb.toString();
		}
		
		public void registerUser(SSAInstruction i) {
			
				
				
			
		}
		
		public int hashCode() {
			return newInstr.hashCode();
		}
		
		public boolean equals(Object o) {
			if (o instanceof Intent) {
				Intent i = (Intent) o;
				return newInstr.equals(i.getCreationInstruction());
			}
			return false;
		} 
		
		public SSANewInstruction getCreationInstruction() {
			return newInstr;
		}
		
		public int getSSAVar() {
			return newInstr.getDef(0);
		}
		
	}
	
	HashMap<SSAProgramPoint, Intent> intents = null;
	private IR ir;
	private DefUse du;
	
	
	
	/**
	 * Invoke this to fill up ...
	 */
	public void prepare() {
		intents = new HashMap<SSAProgramPoint, Intent>();
		for (CGNode n : cg) {
			ir = n.getIR();			
			printedST = false;
			/* Null for JNI methods */
			if (ir == null) {
				E.log(2, "Skipping: " + n.getMethod().toString());
				continue;				
			}
			
			//E.log(1, n.getMethod().getSignature().toString());
			
			HashSet<SSAProgramPoint> newInsts = new HashSet<SSAProgramPoint>();
			for (Iterator<NewSiteReference> it = ir.iterateNewSites(); //ir.iterateAllInstructions(); 
					it.hasNext();) {				
				SSANewInstruction newi = ir.getNew(it.next());
				if (newi.getConcreteType().toString().equals("<Application,Landroid/content/Intent>")) {
					SSAProgramPoint pp = new SSAProgramPoint(n, newi);
					newInsts.add(pp);
				}
			}
			
			/* After the search is done, do the defuse only if there are interesting results */
			if (newInsts.size() > 0) {
				//System.out.println(ir);
				du = new DefUse(ir);
				for (SSAProgramPoint pp : newInsts) {
					SSAInstruction instruction = pp.getInstruction();
					if (instruction instanceof SSANewInstruction) {
						SSANewInstruction newInstr = (SSANewInstruction) instruction;					
						Intent intent = new Intent(newInstr);
						int ssaVar = intent.getSSAVar();
						for(Iterator<SSAInstruction> uses = du.getUses(ssaVar); uses.hasNext(); ) {
							SSAInstruction user = uses.next();
							if (user instanceof SSAInvokeInstruction) {
								SSAInvokeInstruction inv = (SSAInvokeInstruction) user;
								String atom = inv.getDeclaredTarget().getSelector().toString();
								if (atom.equals(new String("setFlags(I)Landroid/content/Intent;"))) {
									
								}
								else if (atom.equals(new String("setAction(Ljava/lang/String;)Landroid/content/Intent;"))) {
									
								}
								else if (atom.equals(new String("setType(Ljava/lang/String;)Landroid/content/Intent;"))) {
									
								}
								else if (atom.equals(new String("setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;"))) {
									//Make this generic - recursive	
									E.log(1, n.getMethod().getSignature().toString());
									//This should be the ComponentName - so it should not give a NPE
									
									int def = inv.getUse(1);
									
									
									
									E.log(1, "Looking for: " + def);

									Collection<SSAInstruction> defFromVarInt = getDefFromVarInt(def);
								}
							}
							else if (user instanceof SSAReturnInstruction) {
								
							}
							else if (user instanceof SSAPhiInstruction) {
								
							}
							else {
								E.log(1, "Unable to register: " + user.toString());
							}
							
							//E.log(1, "Adding intent use");	
						}
						intents.put(pp, intent);
						

					}
				}
			}
		}		
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
	
	
	
	public void printIntents() {
		for (Entry<SSAProgramPoint, Intent> i : intents.entrySet()) {
			E.log(1, i.toString());
		}
	}

}

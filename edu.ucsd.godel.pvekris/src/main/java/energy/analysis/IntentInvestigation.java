package energy.analysis;

import java.util.ArrayList;
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

import energy.util.E;
import energy.util.SSAProgramPoint;

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
	
	/**
	 * Invoke this to fill up ...
	 */
	public void prepare() {
		intents = new HashMap<SSAProgramPoint, Intent>();
		for (CGNode n : cg) {
			IR ir = n.getIR();			
			
			/* Null for JNI methods */
			if (ir == null) {
				E.log(2, "Skipping: " + n.getMethod().toString());
				continue;				
			}
			
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
				DefUse du = new DefUse(ir);
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
									int def = inv.getDef(1);
									SSAInstruction def2 = du.getDef(def);
									
									if (def2 instanceof SSAPhiInstruction) {
										SSAPhiInstruction phi = (SSAPhiInstruction) def2;
									}
									
									
								}
								
								
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
	
	public void printIntents() {
		for (Entry<SSAProgramPoint, Intent> i : intents.entrySet()) {
			E.log(1, i.toString());
		}
	}

}

package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class SQLiteOpenHelper extends Context{

  public SQLiteOpenHelper(GlobalManager gm, IClass c) {
	    super(gm, c);
  }

}

package edu.ucsd.energy.util;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.util.intset.IntIterator;

import edu.ucsd.energy.analysis.Options;


public class Util {

	public static <T> Set<T> iteratorToSet(Iterator<T> itr) {
		HashSet<T> hashSet = new HashSet<T>();
		while (itr.hasNext()) {
			hashSet.add(itr.next());
		}
		return hashSet;
	}

	public Collection<Integer> intIteratorToSet(IntIterator itr) {
		HashSet<Integer> hashSet = new HashSet<Integer>();
		while (itr.hasNext()) {
			hashSet.add(itr.next());
		}
		return hashSet;
	}


	public <T> Collection<T> flattenCollection(Collection<Collection<T>> s) {
		HashSet<T> result = new HashSet<T>();
		for (Collection<T> a : s) {
			for(T b : a) {
				result.add(b);
			}
		}
		return result;	  
	}
}


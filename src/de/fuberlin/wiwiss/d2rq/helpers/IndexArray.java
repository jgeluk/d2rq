package de.fuberlin.wiwiss.d2rq.helpers;

/**
 * An IndexArray is an array of (wrapped) counters. 
 */
public class IndexArray {

	public final int n;
	public final int[] ranges;
	public int[] counters;
	public int idx; // last index changed by next() or prev() operation (or n, if restart)
	
	public IndexArray(int[] ranges) {
		this.ranges = ranges;
		n=ranges.length;
		counters=new int[n];
		idx=0;
	}
	
	public int lastIndexChanged() {
	    return idx;
	}

	public boolean hasNext() {
		int i=0;
		do {
			if (counters[i]+1<ranges[i])
				return true;
		} while (i<n);
		return false;
	}
	public boolean hasPrev() {
		int i=0;
		do {
			if (counters[i]>0)
				return true;
		} while (i<n);
		return false;
	}
	
	public int next() { // returns the highest counter number, that was changed (or n)
		idx=0;
		do {
			counters[idx]++;
			if (counters[idx]<ranges[idx])
				return idx;
			else
				counters[idx]=0;
			idx++;
		} while (idx<n);
		return n;
	}
	
	public int prev() { // returns the highest counter number, that was changed
	    idx=0;
		do {
			counters[idx]--;
			if (counters[idx]>=0)
				return idx;
			else
				counters[idx]=ranges[idx]-1;
			idx++;
		} while (idx<n);
		return n;
	}
	
	
	public static void test(){
	    IndexArray a=new IndexArray(new int[]{2,3,4});
	    for (int i=0; i<50; i++) {
	        System.out.print(a.next());
	        System.out.print(": ");
	        for (int j=0; j<a.counters.length; j++) {
	            System.out.print(a.counters[j]);
	            System.out.print(" ");
	        }	            
	        System.out.println(";");
	    }
	}

}
package net.evmodder.DropHeads;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class LRUCache<K, V>{
	private final int MAX_SIZE;
	private Map<K, V> map;
	private Queue<K> fifo;

	public LRUCache(int max_size) {
		MAX_SIZE = max_size;
		map = new HashMap<>();
		fifo = new LinkedList<>();
	}
	public V get(K k){
		return map.get(k);
	}
	public V put(K k, V v){
		V old_v = map.put(k, v);
		if(old_v == null){// else, move k to end of fifo?
			fifo.add(k);
			if(fifo.size() > MAX_SIZE) map.remove(fifo.remove());
		}
		return old_v;
	}
}
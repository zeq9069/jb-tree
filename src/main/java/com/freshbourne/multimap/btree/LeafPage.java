/**
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 * http://creativecommons.org/licenses/by-nc/3.0/
 * For alternative conditions contact the author.
 * 
 * (c) 2010 "Robin Wenglewski <robin@wenglewski.de>"
 */
package com.freshbourne.multimap.btree;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Observable;

import com.freshbourne.io.DataPage;
import com.freshbourne.io.DataPageManager;
import com.freshbourne.io.DynamicDataPage;
import com.freshbourne.io.FixLengthSerializer;
import com.freshbourne.io.HashPage;
import com.freshbourne.io.NoSpaceException;
import com.freshbourne.io.Page;
import com.freshbourne.io.PagePointer;
import com.freshbourne.io.ResourceManager;
import com.freshbourne.io.Serializer;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * This B-Tree-Leaf stores entries by storing the keys and values in seperate pages
 * and keeping track only of the pageId and offset.
 * 
 * @author "Robin Wenglewski <robin@wenglewski.de>"
 *
 * @param <K> KeyType
 * @param <V> ValueType
 */
public class LeafPage<K,V> extends Observable implements Node<K,V>, Page {
	
	private  ByteBuffer buffer;
	private  FixLengthSerializer<PagePointer, byte[]> pointerSerializer;
	
	private final HashPage hashPage;
	
	private DataPageManager<K> keyPageManager;
	private DataPageManager<V> valuePageManager;
	
	// right now, we always store key/value pairs. If the entries are not unique,
	// it could make sense to store the key once with references to all values
	//TODO: investigate if we should do this
	private  int serializedPointerSize;
	private  int maxEntries;
	
	// counters
	private int numberOfEntries = 0;
	
	private int lastKeyPageId = -1;
	private int lastKeyPageRemainingBytes = -1;
	
	private int lastValuePageId = -1;
	private int lastValuePageRemainingBytes = -1;	
	
	//TODO: ensure that the pointerSerializer always creates the same (buffer-)size!
	@Inject
	LeafPage(
			HashPage hashPage,
			DataPageManager<K> keyPageManager,
			DataPageManager<V> valuePageManager,
			FixLengthSerializer<PagePointer, byte[]> pointerSerializer
			){
		
		this.hashPage = hashPage;
		this.buffer = hashPage.body();
		this.keyPageManager = keyPageManager;
		this.valuePageManager = valuePageManager;
		this.pointerSerializer = pointerSerializer;
		
		this.serializedPointerSize = pointerSerializer.serializedLength(PagePointer.class);
		
		// one pointer to key, one to value
		maxEntries = buffer.capacity() / (serializedPointerSize * 2); 
	}
	
	/* (non-Javadoc)
	 * @see com.freshbourne.multimap.MultiMap#add(java.lang.Object, java.lang.Object)
	 */
	@Override
	public void add(K key, V value) throws Exception {
		if(numberOfEntries == maxEntries)
			throw new Exception();
		
		// add to data_page
		PagePointer keyPointer = storeKey(key);
		PagePointer valuePointer = storeValue(value);
		
		
		// add to header
		
		//byte[] keyBytes = keySerializer.serialize(key);
		//byte[] valueBytes = valueSerializer.serialize(value);
//		
//		// make sure the generated bytes fit in a page
//		if(keyBytes.length > page.body().capacity())
//			throw new SerializationToLargeException(key);
//		if(valueBytes.length > page.body().capacity())
//			throw new SerializationToLargeException(value);
//		
//		
//		storeKeyAndValueBytes(keyBytes, valueBytes);
	}

	/**
	 * @param keyBytes
	 * @param valueBytes
	 * @throws IOException 
	 */
	private void storeKeyAndValueBytes(byte[] keyBytes,
			byte[] valueBytes) throws IOException {
//		RawPage keyPage;
//		RawPage valuePage;
//		
//		if(keyBytes.length > lastKeyPageRemainingBytes){
//			keyPage = resourceManager.newPage();
//		}  else {
//			keyPage = resourceManager.readPage(lastKeyPageId);
//		}
//		
//		if(valueBytes.length > lastValuePageRemainingBytes){
//			try{
//				valuePage = resourceManager.newPage();
//			} finally {
//				//TODO: unfortunately, we dont have this yet.
//				//if(newKeyPage != null)
//					// resourceManager.removePage()
//			}
//		} else {
//			valuePage = resourceManager.readPage(lastValuePageId);
//		}
//		
//		DataPage keyDataPage = new DynamicDataPage(keyPage.body(), pointerSerializer);
//		DataPage valueDataPage = new DynamicDataPage(valuePage.body(), pointerSerializer);
//		
//		// int keyPos = keyDataPage.add(keyBytes);
//		
//		// pagepointer, we use it different: offset is number of the value
//		
		
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.multimap.btree.Node#size()
	 */
	@Override
	public int size() {
		return numberOfEntries;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.multimap.MultiMap#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(K key) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.multimap.MultiMap#getFirst(java.lang.Object)
	 */
	@Override
	public V getFirst(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.multimap.MultiMap#get(java.lang.Object)
	 */
	@Override
	public V[] get(K key) {
		// TODO Auto-generated method stub
		return null;
	}


	/* (non-Javadoc)
	 * @see com.freshbourne.multimap.MultiMap#remove(java.lang.Object)
	 */
	@Override
	public V[] remove(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.multimap.MultiMap#remove(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V remove(K key, V value) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.multimap.MultiMap#clear()
	 */
	@Override
	public void clear() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @return the maximal number of Entries
	 */
	public int getMaxEntries() {
		return maxEntries;
	}

	private PagePointer storeKey(K key) throws Exception{
		DataPage<K> page = keyPageManager.createPage();
		new PagePointer(page.hashPage().id(),page.add(key));
		return null;
	}
	
	private PagePointer storeValue(V value){
		return null;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.io.Page#hashPage()
	 */
	@Override
	public HashPage hashPage() {
		return null;
	}
}
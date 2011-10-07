/*
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 * http://creativecommons.org/licenses/by-nc/3.0/
 * For alternative conditions contact the author.
 *
 * Copyright (c) 2010 "Robin Wenglewski <robin@wenglewski.de>"
 */
package com.freshbourne.btree;


import com.freshbourne.io.DataPageManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class LeafNodeSpec {
	
	private final static Injector injector;
	private final static String path = "/tmp/leaf_spec";
	private LeafNode<Integer, String> leaf;
	private LeafPageManager<Integer, String> lpm;
	
	private int key1 = 1;
	private int key2 = 2;
	
	private String value1 = "val1";
	private String value2 = "value2";
	
	
	static {
		if((new File(path)).exists())
			(new File(path)).delete();
		
		injector = Guice.createInjector(new BTreeModule(path));
	}
	
	@Before public void setUp(){
		lpm = injector.getInstance(Key.get(new TypeLiteral<LeafPageManager<Integer, String>>(){}));
		leaf = lpm.createPage();
		}
	
	@Test public void shouldBeAbleToInsertAndGet(){
		leaf.insert(key1, value1);
		assertTrue(leaf.containsKey(key1));
		assertEquals(1, leaf.getNumberOfEntries());
		assertEquals(1, leaf.get(key1).size());
		assertEquals(value1, leaf.get(key1).get(0));
	}
	
	@Test public void shouldBeAbleToGetLastKeyAndPointer(){
		leaf.insert(key1, value1);
		assertNotNull(leaf.getLastLeafKey());
		assertNotNull(leaf.getLastLeafKeySerialized());
		
		leaf.insert(key2, value2);
		assertNotNull(leaf.getLastLeafKey());
		assertNotNull(leaf.getLastLeafKeySerialized());
	}
	
	@Test public void shouldAlwaysWorkAfterReload(){
		for(int i = 0; i < 5; i++){
			leaf.insert(key1, value1);
		}
		leaf.insert(key2, value2);
		assertEquals(6, leaf.getNumberOfEntries());
		leaf.load();
		assertEquals(6, leaf.getNumberOfEntries());
		assertEquals(1, leaf.get(key2).size());
		
	}
	
	@Test public void shouldAtSomePointReturnAValidAdjustmentAction(){
		AdjustmentAction<Integer, String> action;
		do{
			action = leaf.insert(key1, value1);
		} while(action == null);
		
		DataPageManager<Integer> keyPageManager = injector.getInstance(Key.get(new TypeLiteral<DataPageManager<Integer>>(){}));
		
		assertNotNull(leaf.getLastLeafKey());
		assertEquals(AdjustmentAction.ACTION.INSERT_NEW_NODE, action.getAction());
		
		assertNotNull(action.getSerializedKey());
		
		
		// this should still work and not throw an exception
		stateTest(leaf);
		LeafNode<Integer, String> newLeaf = lpm.getPage(action.getPageId());;
		stateTest(newLeaf);
	}
	
	private void stateTest(LeafNode<Integer, String> leaf){
		Integer k = leaf.getLastLeafKey();
		assertNotNull(leaf.get(k));
		assertTrue(leaf.containsKey(k));
		
		// all keys should be accessible
		for(int i = 0; i < leaf.getNumberOfEntries(); i++){
			Integer key = leaf.getKeyAtPosition(i);
			assertNotNull(k);
			assertTrue(leaf.containsKey(key));
			
		}
		assertEquals(k, leaf.getKeyAtPosition(leaf.getNumberOfEntries() - 1));

	}
	
	@Test
	public void shouldContainAddedEntries() {
		leaf.insert(key1, value1);
		assertTrue(leaf.containsKey(key1));
		assertEquals(1, leaf.get(key1).size());
		assertEquals(value1, leaf.get(key1).get(0));
		assertEquals(1, leaf.getNumberOfEntries());
		
		leaf.insert(key1, value2);
		assertTrue(leaf.containsKey(key1));
		assertEquals(2, leaf.get(key1).size());
		assertTrue(leaf.get(key1).contains(value1));
		assertTrue(leaf.get(key1).contains(value2));
		assertEquals(2, leaf.getNumberOfEntries());
		
		leaf.insert(key2, value2);
		assertTrue(leaf.containsKey(key2));
		assertEquals(1, leaf.get(key2).size());
		assertTrue(leaf.get(key1).contains(value2));
		assertTrue(leaf.get(key1).contains(value1));
		assertTrue(leaf.get(key1).size() == 2);
		assertEquals(3, leaf.getNumberOfEntries());
	}
	
	@Test
	public void removeWithValueArgumentShouldRemoveOnlyThisValue() {
		leaf.insert(key1, value1);
		leaf.insert(key1, value2);
		leaf.insert(key2, value2);
		
		assertEquals(3, leaf.getNumberOfEntries());
		leaf.remove(key1, value2);
		assertEquals(1, leaf.get(key1).size());
		assertEquals(value1, leaf.get(key1).get(0));
		assertEquals(value2, leaf.get(key2).get(0));
	}
	

	@Test
	public void prependEntriesShouldWork(){
		LeafNode<Integer, String> leaf2 = lpm.createPage();
		
		int totalInserted = 0;
		
		// fill leaf
		for(int i = 0; i < leaf.getMaximalNumberOfEntries(); i++){
			assertNull(leaf.insert(i, "val"));
			totalInserted++;
		}
		
		testPrepend(leaf, leaf2);
		totalInserted++;
		assertEquals(totalInserted,leaf.getNumberOfEntries() + leaf2.getNumberOfEntries());

		// should work again, when we have to actually move some entries in leaf2
		for (int i = leaf.getNumberOfEntries(); i < leaf
				.getMaximalNumberOfEntries(); i++) {
			assertNull(leaf.insert(-1 * i, "val"));
			totalInserted++;
		}

		testPrepend(leaf, leaf2);
		totalInserted++;
		assertEquals(totalInserted,
				leaf.getNumberOfEntries() + leaf2.getNumberOfEntries());
		
	}
	
	private void testPrepend(LeafNode<Integer, String> leaf1, LeafNode<Integer, String> leaf2){
		leaf1.setNextLeafId(leaf2.getId());
		
		// insert key so that move should happen
		AdjustmentAction<Integer, String> action = leaf1.insert(1, "value");
		
		// an update key action should be passed up
		assertNotNull(action);
		
		// make sure leaf structures are in tact
		assertEquals(leaf1.getLastLeafKey(), leaf1.getKeyAtPosition(leaf1.getNumberOfEntries() - 1));
		
		for(int key : leaf1.getKeySet()){
			assertNotNull(leaf1.get(key));
		}
		
		for(int key : leaf2.getKeySet()){
			assertNotNull(leaf2.get(key));
		}
	}
	

}
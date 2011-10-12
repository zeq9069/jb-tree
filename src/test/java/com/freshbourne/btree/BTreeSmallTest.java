/*
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 * http://creativecommons.org/licenses/by-nc/3.0/
 * For alternative conditions contact the author.
 *
 * Copyright (c) 2010 "Robin Wenglewski <robin@wenglewski.de>"
 */

package com.freshbourne.btree;

import com.freshbourne.comparator.IntegerComparator;
import com.freshbourne.io.FileResourceManager;
import com.freshbourne.io.PageManager;
import com.freshbourne.io.PageSize;
import com.freshbourne.io.RawPage;
import com.freshbourne.serializer.FixLengthSerializer;
import com.freshbourne.serializer.FixedStringSerializer;
import com.freshbourne.serializer.IntegerSerializer;
import com.freshbourne.serializer.Serializer;
import com.google.inject.*;
import com.google.inject.util.Modules;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

public class BTreeSmallTest {

	private static Injector                injector;
	private        BTree<Integer, Integer> tree;
	private static final Logger LOG       = Logger.getLogger(BTreeSmallTest.class);
	private static final int    PAGE_SIZE = InnerNode.Header.size() + 3 * (2 * Integer.SIZE / 8) + Integer.SIZE / 8;
	// 3 keys, 4 values
	private static final String FILE_PATH = "/tmp/btree-small-test";


	static {
		injector = Guice.createInjector(
				Modules.override(new BTreeModule(FILE_PATH)).with(new AbstractModule() {
					@Override protected void configure() {
						bind(Integer.class).annotatedWith(PageSize.class).toInstance(PAGE_SIZE);
					}
				}));
	}

	@Before
	public void setUp() {
		new File(FILE_PATH).delete();
		tree = injector.getInstance(Key.get(new TypeLiteral<BTree<Integer, Integer>>() {
		}));
	}

	@Test
	public void ensurePageSizeIsSmall() {
		assertEquals(PAGE_SIZE, injector.getInstance(FileResourceManager.class).pageSize());
	}

	@Test
	public void testsWorking() {
		assertTrue(true);
	}

	@Test
	public void testInitialize() throws IOException {
		tree.initialize();
		assertTrue(tree.isValid());
	}


	@Test
	public void testMultiLevelInsertForward() throws IOException {
		int count = 1000;

		tree.initialize();

		for (int i = 0; i < count; i++) {

			assertTrue(tree.isValid());
			LOG.info("i = " + i);
			tree.add(i, i);
			tree.checkStructure();
			LOG.info("Depth: " + tree.getDepth());
			Iterator<Integer> iterator = tree.getIterator();

			int latest = iterator.next();
			for (int j = 0; j <= i - 1; j++) {
				int next = iterator.next();
				assertTrue(latest <= next);
				latest = next;
			}

			assertFalse(iterator.hasNext());
		}


		assertEquals(count, tree.getNumberOfEntries());
		Iterator<Integer> iterator = tree.getIterator();

		int latest = iterator.next();
		for (int i = 0; i < count - 1; i++) {
			int next = iterator.next();
			assertTrue(latest <= next);
			latest = next;
		}
		assertFalse(iterator.hasNext());
	}

	@Test
	public void testMultiLevelInsertBackward() throws IOException {
		int count = 1000;

		tree.initialize();

		for (int i = 0; i < count; i++) {

			if (i == 9)
				LOG.debug("DEBUG");

			assertTrue(tree.isValid());
			LOG.info("i = " + i);
			tree.add(count - i, count - i);
			tree.checkStructure();
			LOG.info("Depth: " + tree.getDepth());
			Iterator<Integer> iterator = tree.getIterator();

			int latest = iterator.next();
			for (int j = 0; j <= i - 1; j++) {
				int next = iterator.next();
				assertTrue(latest <= next);
				latest = next;
			}

			assertFalse(iterator.hasNext());
		}


		assertEquals(count, tree.getNumberOfEntries());
		Iterator<Integer> iterator = tree.getIterator();

		int latest = iterator.next();
		for (int i = 0; i < count - 1; i++) {
			int next = iterator.next();
			assertTrue(latest <= next);
			latest = next;
		}
		assertFalse(iterator.hasNext());
	}

	@Test
	public void testLargeKeyValues() throws IOException {
		// create a new injector with large pagesize and string-serialization for 1000 bytes
		Injector newInjector = Guice.createInjector(Modules.override(new BTreeModule
				((FILE_PATH))).with(new AbstractModule() {
			@Override protected void configure() {
				bind(Integer.class).annotatedWith(PageSize.class).toInstance(PageSize.DEFAULT_PAGE_SIZE);
				bind(new TypeLiteral<Serializer<String, byte[]>>() {
				}).toInstance(FixedStringSerializer.INSTANCE_1000);
				bind(new TypeLiteral<FixLengthSerializer<String, byte[]>>() {
				}).toInstance(FixedStringSerializer.INSTANCE_1000);

			}
		}));

		// ensure that new injector is working
		assertEquals(1000, newInjector.getInstance(Key.get(new TypeLiteral<FixLengthSerializer<String, byte[]>>() {
		})).getSerializedLength());

		// initialize new btree
		new File(FILE_PATH).delete();
		BTree<String, String> newTree = newInjector.getInstance(Key.get(new TypeLiteral<BTree<String, String>>() {
		}));
		newTree.initialize();


		// do the actual test
		int count = 100;
		for (int i = 0; i < count; i++) {
			if (i == 24)
				LOG.debug("DEBUG");

			LOG.debug("i=" + i);
			newTree.add("" + i, "" + i);
			newTree.checkStructure();
		}

		Iterator<String> iterator = newTree.getIterator();
		for (int i = 0; i < count; i++) {
			assertTrue(iterator.hasNext());
			LOG.debug("got value: " + iterator.next());
		}
		assertFalse(iterator.hasNext());
	}

	@Test
	public void defaultBTreeModule() throws IOException {
		File file = new File("/tmp/defaultBTreeModule");
		file.delete();

		Injector i = Guice.createInjector(new BTreeModule(file.getAbsolutePath()));
		BTree<Integer, Integer> t = i.getInstance(
				Key.get(new TypeLiteral<BTree<Integer, Integer>>() {
				}));
		t.initialize();
		t.sync();

		assertTrue(file.exists());
	}

	@Test
	public void manualConstructor() throws IOException {
		String filePath = "/tmp/manualConstructorTestBtree";
		File file = new File(filePath);
		file.delete();

		FileResourceManager pm = new FileResourceManager(file);
		pm.open();

		BTree<Integer, String> btree =
				new BTree<Integer, String>(pm, IntegerSerializer.INSTANCE, FixedStringSerializer.INSTANCE,
						IntegerComparator.INSTANCE);

		btree.initialize();
		btree.sync();

		assertTrue(file.exists());
	}

	@Test
	public void factoryConstructor() throws IOException {
		File file = new File("/tmp/defaultBTreeModule");
		file.delete();


		Injector i = Guice.createInjector(new BTreeModule());
		BTreeFactory factory = i.getInstance(BTreeFactory.class);
		BTree<Integer, String> btree = factory.get(file, IntegerSerializer.INSTANCE, FixedStringSerializer.INSTANCE,
				IntegerComparator.INSTANCE);
		btree.initialize();
		btree.sync();

		assertTrue(file.exists());
	}

	@Test
	public void staticMethodConstructor() throws IOException {
		File file = new File("/tmp/btree-test");
		file.delete();

		BTree<Integer, String> btree = BTree.create(file, IntegerSerializer.INSTANCE, FixedStringSerializer.INSTANCE,
				IntegerComparator.INSTANCE);
		btree.initialize();
		btree.sync();

		assertTrue(file.exists());
	}

	@Test
	public void integerStringTree() throws IOException {
		File file = new File("/tmp/btree-test");
		file.delete();

		BTree<Integer, String> btree =
				BTree.create(file, IntegerSerializer.INSTANCE, FixedStringSerializer.INSTANCE_1000,
						IntegerComparator.INSTANCE);

		btree.initialize();

		int count = 50;

		for (int i = 0; i < count; i++) {
			LOG.debug("ROUND: " + i);

			btree.add(i, "" + i);
			btree.sync();

			BTree<Integer, String> btree2 =
					BTree.create(file, IntegerSerializer.INSTANCE, FixedStringSerializer.INSTANCE_1000,
							IntegerComparator.INSTANCE);
			btree2.load();


			Iterator<String> iterator = btree2.getIterator();

			for (int j = 0; j <= i; j++) {
				assertTrue(iterator.hasNext());
				LOG.debug("next value:" + iterator.next());
			}

			assertFalse(iterator.hasNext());


		}

		btree.sync();

		btree = BTree.create(file, IntegerSerializer.INSTANCE, FixedStringSerializer.INSTANCE_1000,
				IntegerComparator.INSTANCE);
		btree.load();

		Iterator<String> iterator = btree.getIterator();

		for (int i = 0; i < count; i++) {
			assertTrue(iterator.hasNext());
			LOG.debug("next value:" + iterator.next());
		}

		assertFalse(iterator.hasNext());

	}

	@Test
	public void iteratorsWithoutParameters() throws IOException {
		tree.initialize();
		fillTree(tree, 1000);
		Iterator<Integer> iterator = tree.getIterator();
		for(int i = 0; i<1000;i++)
			assertEquals(i, (int) iterator.next());

		assertFalse(iterator.hasNext());
	}

	@Test
	public void iteratorsWithRanges() throws IOException {
		tree.initialize();
		fillTree(tree, 100);
		List<Range<Integer>> rangeList = new ArrayList<Range<Integer>>();
		rangeList.add(new Range(-5, 5));

		// 49 - 56
		rangeList.add(new Range(50, 55));
		rangeList.add(new Range(52, 53));
		rangeList.add(new Range(49, 53));
		rangeList.add(new Range(52, 56));

		rangeList.add(new Range(95, 1000));

		Iterator<Integer> iterator = tree.getIterator(rangeList);

		for(int i=0;i<=5;i++){
			assertEquals(i, (int) iterator.next());
		}

		for(int i=49; i<=56; i++)
			assertEquals(i, (int) iterator.next());

		for(int i=95;i< 100;i++){
			assertEquals(i, (int) iterator.next());
		}

		assertFalse(iterator.hasNext());
	}

	@Test
	public void iteratorsWithStartEndGiven() throws IOException {
		tree.initialize();
		fillTree(tree, 100);
		Iterator<Integer> iterator = tree.getIterator();
		for (int i = 0; i < 100; i++)
			assertEquals(i, (int) iterator.next());
		assertFalse(iterator.hasNext());

		iterator = tree.getIterator(-50, 50);
		for (int i = 0; i <= 50; i++)
			assertEquals(i, (int) iterator.next());
		assertFalse(iterator.hasNext());

		iterator = tree.getIterator(50,150);
		for (int i = 50; i < 100; i++)
			assertEquals(i, (int) iterator.next());
		assertFalse(iterator.hasNext());
	}

	private void fillTree(BTree<Integer, Integer> tree, int count) {
		for (int i = 0; i < count; i++) {
			tree.add(i, i);
		}
	}

	@Test
	public void close() throws IOException {
		tree.initialize();
		fillTree(tree, 100);
		tree.close();
		assertFalse(tree.isValid());
		assertFalse(tree.getResourceManager().isOpen());
	}
}

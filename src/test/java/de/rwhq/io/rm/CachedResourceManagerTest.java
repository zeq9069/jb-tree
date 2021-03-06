/*
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 *
 * http://creativecommons.org/licenses/by-nc/3.0/
 *
 * For alternative conditions contact the author.
 *
 * Copyright (c) 2011 "Robin Wenglewski <robin@wenglewski.de>"
 */

package de.rwhq.io.rm;

import com.google.common.cache.Cache;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.fest.assertions.Assertions.assertThat;


public class CachedResourceManagerTest {

	private ResourceManager rm;
	private static final Logger LOG  = Logger.getLogger(CachedResourceManagerTest.class);
	private static       File   file = new File("/tmp/CachedResourceManagerTest");

	@Before
	public void setUp() throws IOException {
		file.delete();
		rm = new ResourceManagerBuilder().file(file).cacheSize(0).cacheSize(1000).open().build();
	}

	public static class ResourceManagerTestImpl extends ResourceManagerTest {

		@Override
		protected ResourceManager resetResourceManager() {
			file.delete();
			return new ResourceManagerBuilder().file(file).cacheSize(0).cacheSize(1000).open().build();
		}
	}

	@Test
	public void open() throws IOException {
		rm.close();
		file.delete();
		rm = new ResourceManagerBuilder().file(file).cacheSize(0).build();
		assertThat(rm.isOpen()).isFalse();
		rm.open();
		assertThat(rm.isOpen()).isTrue();
	}


	@Test
	public void cache() throws IOException {
		assertThat(rm.isOpen()).isTrue();
		final int count = 10;
		final RawPage[] pages = new RawPage[count];

		for (int i = 0; i < count; i++) {
			pages[i] = rm.createPage();
		}

		final CachedResourceManager crm = (CachedResourceManager) rm;
		final Cache<Integer, RawPage> cache = crm.getCache();

		final Random random = new Random();
		for (int i = 0; i < count * 10; i++) {
			rm.getPage(pages[random.nextInt(10)].id());
		}

		assertThat(cache.stats().hitCount()).isEqualTo(count * 10);
	}


	@Test
	public void testAutoSave() throws IOException {
		final CachedResourceManager crm = (CachedResourceManager) rm;
		final RawPage p = rm.createPage();
		final int testInt = 5343;
		p.bufferForWriting(0).putInt(testInt);

		for (int i = 0; i < crm.getCacheSize() * 2; i++) {
			rm.createPage();
		}

		assertThat(crm.getCache().asMap().containsKey(p)).isFalse();
		assertThat(rm.getPage(p.id()).bufferForReading(0).getInt()).isEqualTo(testInt);
	}

	@Test
	public void closeShouldInvalidateCache() throws IOException {
		final RawPage page = rm.createPage();
		rm.close();
		rm.open();
		assertThat(rm.getPage(page.id())).isNotSameAs(page);
	}
}

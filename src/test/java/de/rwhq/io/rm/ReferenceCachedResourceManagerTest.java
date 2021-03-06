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

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.fest.assertions.Assertions.assertThat;

public class ReferenceCachedResourceManagerTest {
	private ResourceManager rm;
	private static final Logger LOG  = Logger.getLogger(ReferenceCachedResourceManagerTest.class);
	private static File   file = new File("/tmp/ReferenceCachedResourceManagerTest");

	@Before
	public void setUp() throws IOException {
		file.delete();
		rm = new ResourceManagerBuilder().file(file).cacheSize(0).useReferenceCache(true).open().build();
	}

	public static class ResourceManagerTestImpl extends ResourceManagerTest {

		@Override
		protected ResourceManager resetResourceManager() {
			file.delete();
			return new ResourceManagerBuilder().file(file).cacheSize(0).useReferenceCache(true).open().build();
		}
	}

	@Test
	public void instanceOfReferenceCachedResourceManager(){
		assertThat(rm).isInstanceOf(ReferenceCachedResourceManager.class);
	}


	@Test
	public void shouldReturnSameInstanceIfAlreadyInMemory(){
		final RawPage page = rm.createPage();

		// invalidate cache
		rm.createPage();
		rm.createPage();

		assertThat(rm.getPage(page.id())).isSameAs(page);
	}
}

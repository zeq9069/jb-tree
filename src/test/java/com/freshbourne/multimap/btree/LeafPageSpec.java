/*
 * Copyright (c) 2011 Robin Wenglewski <robin@wenglewski.de>
 *
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 * http://creativecommons.org/licenses/by-nc/3.0/
 * For alternative conditions contact the author.
 */
package com.freshbourne.multimap.btree;

import com.freshbourne.io.DynamicDataPage;
import com.freshbourne.io.FileResourceManagerModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNotNull;

public class LeafPageSpec {
	
	private LeafPage<Integer, String> leaf;
	private DynamicDataPage<String> stringData;
	private DynamicDataPage<Integer> intData;
	
	private static final Injector injector;

    static {
        injector = Guice.createInjector(
                new FileResourceManagerModule(new File("/tmp/leafnodespec")),
                new BTreeModule()
        );
    }
	
	@Before
	public void setUp(){
		
		stringData = injector.getInstance(DynamicDataPage.class);
		
	}
	
	
	@Test
	public void setUpShouldHaveWorked(){
		assertNotNull(leaf);
	}

}

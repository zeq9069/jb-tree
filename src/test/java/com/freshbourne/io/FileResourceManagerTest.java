/**
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 * http://creativecommons.org/licenses/by-nc/3.0/
 * For alternative conditions contact the author.
 * 
 * (c) 2010 "Robin Wenglewski <robin@wenglewski.de>"
 */
package com.freshbourne.io;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.freshbourne.io.FileResourceManager;
import com.freshbourne.io.Page;
import com.freshbourne.io.ResourceManager;
import com.freshbourne.io.ResourceManagerModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import junit.framework.TestCase;

public class FileResourceManagerTest extends TestCase {
	
	private ResourceManager rm;
	private int pageSize = 4048;
	private File file;
	private String path = "/tmp/frm_test";
	
	public void setUp() throws IOException {
		file = new File(path);
		if(file.exists()){
			file.delete();
		}
		rm = new FileResourceManager(file, pageSize);
		rm.open();
		
	}
	
	public void tearDown() throws IOException{
		rm.close();
	}
	
	public void testCreation() throws IOException{
		assertTrue(rm instanceof ResourceManager);
		assertEquals(pageSize,rm.getPageSize());
		assertEquals(0, rm.getNumberOfPages());
	}
	
	public void testPageCreation() throws IOException{
		Page p = rm.newPage();
		
		// test created page
		assertTrue(p instanceof Page);
		assertEquals(pageSize, p.size());
		assertEquals(1, p.getId());
		assertEquals(rm, p.getResourceManager());
		
		// test rm
		assertEquals(1, rm.getNumberOfPages());
		
		// test the page
		assertFalse(p.valid());
		p.initialize();
		assertTrue(p.valid());
				
		// test saving the page
		p.save();
		assertEquals(pageSize, file.length());
		
		rm.close();
		rm.open();
		p = rm.readPage(1);
		assertTrue(p.valid());
		
		// altering the page size should throw an error
		rm.close();
		rm = new FileResourceManager(file, pageSize * 2);
		try{
			rm.open();
			fail("this should throw an error");
		} catch (Exception e) {
		}
	}
	
	
	
}

/**
 * Copyright (C) 2011 Robin Wenglewski <robin@wenglewski.de>
 *
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 * http://creativecommons.org/licenses/by-nc/3.0/
 * For alternative conditions contact the author. 
 */
package com.freshbourne.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceHeaderPage implements ComplexPage {
	private final RawPage rawPage;
	private final List<Long> directory = new ArrayList<Long>();
	private boolean valid = false;

	ResourceHeaderPage(RawPage rawPage){
		this.rawPage = rawPage;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.io.ComplexPage#initialize()
	 */
	@Override
	public void initialize() {
		rawPage.buffer().position(0);
		rawPage.buffer().putInt(1); // 1 pages in this file, the header page
		rawPage.buffer().putLong(rawPage.id());
		directory.add(rawPage.id());
		valid = true;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.io.ComplexPage#load()
	 */
	@Override
	public void load() {
		rawPage.buffer().position(0);
		int numberOfpages = rawPage.buffer().getInt();
		for( int i = 0; i < numberOfpages; i++){
			directory.add(rawPage.buffer().getLong());
		}
		valid = true;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.io.ComplexPage#isValid()
	 */
	@Override
	public boolean isValid() {
		return valid;
	}

	/* (non-Javadoc)
	 * @see com.freshbourne.io.ComplexPage#rawPage()
	 */
	@Override
	public RawPage rawPage() {
		return rawPage;
	}
	
	public void add(Long pageId){
		directory.add(pageId);
	}
	
	public boolean contains(Long id){
		return directory.contains(id);
	}
	
	public int getRealPageNr(Long id){
		return directory.indexOf(id);
	}
	
	public int getNumberOfPages(){
		return directory.size();
	}

	public void writeToResource() throws IOException {
		rawPage.writeToResource();
	}
}
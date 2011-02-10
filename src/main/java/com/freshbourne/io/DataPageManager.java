/*
 * Copyright (c) 2011 Robin Wenglewski <robin@wenglewski.de>
 *
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 * http://creativecommons.org/licenses/by-nc/3.0/
 * For alternative conditions contact the author.
 */

package com.freshbourne.io;

import com.freshbourne.serializer.FixLengthSerializer;
import com.freshbourne.serializer.Serializer;
import com.google.inject.Inject;

import java.io.IOException;

public class DataPageManager<T> implements PageManager<DataPage<T>> {

    private final PageManager<RawPage> bpm;
    private final FixLengthSerializer<PagePointer, byte[]> pointSerializer;
    private final Serializer<T, byte[]> dataSerializer;

    @Inject
    DataPageManager(
    		PageManager<RawPage> bpm,
            FixLengthSerializer<PagePointer, byte[]> pointSerializer,
			Serializer<T, byte[]> dataSerializer
            ){
        this.bpm = bpm;
        this.pointSerializer = pointSerializer;
        this.dataSerializer = dataSerializer;
    }

    @Override
    public DataPage<T> createPage() {
    	DataPage<T> result = new DynamicDataPage<T>(bpm.createPage(), pointSerializer, dataSerializer);
    	result.initialize();
		return result;
    }

    @Override
    public DataPage<T> getPage(long id) {
    	DataPage<T> result = new DynamicDataPage<T>(bpm.getPage(id), pointSerializer, dataSerializer);
    	try {
			result.load();
		} catch (IOException e) {
			throw new IllegalStateException();
		}
        return result;
    }

    @Override
    public void removePage(long id) {
        bpm.removePage(id);
    }

	/* (non-Javadoc)
	 * @see com.freshbourne.io.PageManager#hasPage(long)
	 */
	@Override
	public boolean hasPage(long id) {
		return bpm.hasPage(id);
	}
}

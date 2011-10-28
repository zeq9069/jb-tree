/*
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 *
 * http://creativecommons.org/licenses/by-nc/3.0/
 *
 * For alternative conditions contact the author.
 *
 * Copyright (c) 2011 "Robin Wenglewski <robin@wenglewski.de>"
 */

package com.freshbourne.io;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/** used to configure and build Resource Managers */
public class ResourceManagerBuilder {
	private boolean useCache  = true;
	private boolean useLock   = true;
	private int     cacheSize = 100;
	private int     pageSize  = PageSize.DEFAULT_PAGE_SIZE;
	private boolean open = false;

	private File file = null;

	public ResourceManagerBuilder useCache(boolean cached) {
		this.useCache = cached;
		return this;
	}

	public ResourceManagerBuilder useLock(boolean useLock) {
		this.useLock = useLock;
		return this;
	}

	public ResourceManagerBuilder cacheSize(int cacheSize) {
		checkArgument(cacheSize > 0, "cacheSize must be > 0");
		this.cacheSize = cacheSize;
		return this;
	}

	public ResourceManagerBuilder pageSize(int pageSize) {
		checkArgument(pageSize >= 24, "min pageSize is 24");
		this.pageSize = pageSize;
		return this;
	}

	public ResourceManagerBuilder file(File file) {
		checkNotNull(file, "file must not be null");
		this.file = file;
		return this;
	}

	public ResourceManagerBuilder file(String file) {
		checkNotNull(file, "file must not be null");
		this.file = new File(file);
		return this;
	}

	public ResourceManager build() {
		checkNotNull(file, "file must be set");

		ResourceManager rm = new FileResourceManager(this);
		if (useCache) {
			rm = new CachedResourceManager(rm, cacheSize);
		}

		if(open){
			try {
				rm.open();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return rm;
	}

	public AutoSaveResourceManager buildAutoSave(){
		checkArgument(useCache, "To create an autoSaveResourceManager, useCache must be true");
		return (AutoSaveResourceManager) build();
	}

	public ResourceManagerBuilder open(){
		this.open = true;
		return this;
	}

	int getCacheSize() {
		return cacheSize;
	}

	File getFile() {
		return file;
	}

	int getPageSize() {
		return pageSize;
	}

	boolean useCache() {
		return useCache;
	}

	boolean useLock() {
		return useLock;
	}

}
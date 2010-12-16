/**
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 * http://creativecommons.org/licenses/by-nc/3.0/
 * For alternative conditions contact the author.
 * 
 * (c) 2010 "Robin Wenglewski <robin@wenglewski.de>"
 */
package com.freshbourne.io;

/**
 * A Serializer that serializes always to the same String/Buffer length
 * 
 * @author "Robin Wenglewski <robin@wenglewski.de>"
 *
 */
public interface FixLengthSerializer<InputType, ResultType> extends Serializer<InputType, ResultType> {
	
	/**
	 * @return length of the object returned by {@link #serialize(Object)}
	 */
	public int serializedLength();
}

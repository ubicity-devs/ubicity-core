/**
    Copyright (C) 2013  AIT / Austrian Institute of Technology
    http://www.ait.ac.at

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see http://www.gnu.org/licenses/agpl-3.0.html
 */
package at.ac.ait.ubicity.core.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Jan van Oort
 * 
 * This interface represents an elasticsearch percolator. It is here explicitly for use 
 * by ubicity plugins.
 * 
 * This annotation can be used to mark plugin instances which should be initialized at
 * runtime. If you don't annotate your specific Facet implementation with this annotation, nothing will happen.
 * For example, to specify that a specific class should be treated as a Facet you would 
 * write:<br/><br/>
 * 
 * <code>
 * &#064;Percolator<br/>
 * public class MyPercolatorImpl implements MyPercolator { ... } 
 * </code><br/><br/>
 * 
 * In this case, <code>MyFacet</code> has to extend {@link Percolator }.* 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Percolator {
    
}

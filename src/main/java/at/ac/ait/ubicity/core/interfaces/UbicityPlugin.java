package at.ac.ait.ubicity.core.interfaces;
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
import at.ac.ait.ubicity.core.JSONProducer;
import at.ac.ait.ubicity.core.PluginContext;
import net.xeoh.plugins.base.Plugin;

/**
 *
 * @author Jan van Oort
 * @version 0.2.1
 */ 
public interface UbicityPlugin extends Plugin, Runnable {
   
    
    @Override
    public int hashCode();
    
    @Override
    public boolean equals( Object o );
    
    
    
    /*
     * As there is no abstract superclass for all possible and future plugins, 
     * plugin behaviour can not be enforced. Implementors are technically free to do nothing
     * in this method's implementation. This is, however, a bad idea, because of a design choice 
     * in at.ac.ait.ubicity.core.Core: after having called this method, the Core will wait for a 
     * certain time ( not specified here, but estimated sufficient for any plugin to stop in 
     * a timely and orderly manner ). Once this timeout is over and the Core suspects the plugin 
     * of still running, the plugins Thread is looked up and - somewhat forcefully - terminated.
     */
    public void mustStop();

    public String getName();

    
    public void setContext( PluginContext context );

    
    public PluginContext getContext();
    
    
    
    
    
}

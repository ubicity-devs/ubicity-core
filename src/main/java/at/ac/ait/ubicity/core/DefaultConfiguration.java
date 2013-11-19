
package at.ac.ait.ubicity.core;

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


import at.ac.ait.ubicity.core.constants.Constants;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration.PropertiesConfiguration;

/**
 *
 * @author Jan van Oort
 * 
 * A last-resort or default configuration for ubicity core. 
 * 
 */
final class DefaultConfiguration extends PropertiesConfiguration {

    
    private final static Logger logger = Logger.getLogger( DefaultConfiguration.class.getName() ) ;
    
    static  {
        logger.setLevel( Level.ALL );
    }
    
    
    protected  static String ELASTICSEARCH_HOST = "ubicity.ait.ac.at";
    
    protected  static String ELASTICSEARCH_INDEX = "geo_tweets";
    
    protected static String ELASTICSEARCH_TYPE = "ctweet";
    
    
    
    
    public DefaultConfiguration() {
        //this is still a hack, for the moment, but should work in most cases where no .cf file is present
        fillFrom( Constants.class );
        configureCore();
    }
    
    
    public final void fillFrom( final Class _class )  {
        Field[] _fields = _class.getFields();
        int _fieldCount = _fields.length;
        for( int i = 0; i < _fieldCount; i++ )  {
            int _compositeModifier = _fields[ i ].getModifiers();
            //we only take fields that have String type, and are public static final ( i.e., String constants )
            if( _fields[ i ].getType().equals( String.class ) &&  Modifier.isPublic(  _compositeModifier ) && Modifier.isFinal( _compositeModifier ) && Modifier.isStatic( _compositeModifier ) ) {
                attemptSettingFrom( _fields[ i ] );
            }
        }
    }

    
    private final void attemptSettingFrom( final Field _field) {
        String _fieldName = _field.getName();
        try {
            Field myCorrespondingField = this.getClass().getField( _fieldName );
            if ( myCorrespondingField != null && myCorrespondingField.getType().equals( String.class ) )   {
                myCorrespondingField.set( this, _field.get( null ) );
            }
        }
        catch( NoSuchFieldException | IllegalAccessException | SecurityException nothingToDo )   {
            //log this problem and carry on with life, nothing to do  about it anyways
            logger.fine( this.getClass().getName() + " :: " + nothingToDo.toString() );
        }
    }

    private final void configureCore() {
        Core.server = ELASTICSEARCH_HOST;
        Core.index = ELASTICSEARCH_INDEX;
        Core.type = ELASTICSEARCH_TYPE;
    }
}

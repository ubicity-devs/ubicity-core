/**
    Copyright (C) 2014  AIT / Austrian Institute of Technology
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

package at.ac.ait.ubicity.core;

import at.ac.ait.ubicity.commons.protocol.Answer;
import at.ac.ait.ubicity.commons.protocol.Command;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;



/**
 *
 * @author jan van oort
 */
class Connection extends Thread {

    static int connection_number = 0;
    
    protected Socket client;
    
    protected final Vulture vulture;
    
    protected ObjectInputStream in;
    
    protected ObjectOutputStream out;
    
    
    Connection( final Socket _client, final ThreadGroup _threadGroup, final int _priority, final Vulture _vulture) {
        
        super( _threadGroup, "Connection " + connection_number++ );
        System.out.println( "[CORE] new Connection from " + _client.getInetAddress() );
        this.setPriority( _priority );
        
        client = _client;
        vulture = _vulture;
        this.start();
    }

    
    
    
    
    
    @Override
    public void run() {

        System.out.println( "[CORE] starting up connection from "  + client.getInetAddress() );
        
        
        try {
            in = new ObjectInputStream( client.getInputStream() );
            out = new ObjectOutputStream( client.getOutputStream() );
        }
        catch( IOException e )  {
            try {
                client.close();
                JitIndexingController.fail( e, "Exception while getting Socket Streams for " + this.getName() );
            }
            catch( IOException ioex2 )  {;}
        }
        
        for( ;;)    {
            try {
                final Object o = in.readObject();
                Command _command = ( Command ) o;
                System.out.println("[CORE] received a command:: " + _command.toRESTString() );
                Answer _a = Core.getInstance().forward( _command );
                if( !( _a == null )  ) out.writeObject( _a );
            } 
            catch (IOException | ClassNotFoundException ex) {
                JitIndexingController.fail( ex, "Exception occurred while trying to read Command object for " + this.getName() );
                return;
            } 
            finally {
                synchronized( vulture ) {
                    vulture.notify();
                }
            }
        }
    }
    
}




































package net.minecraft.src;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemoryConnection implements INetworkManager
{
    private static final SocketAddress mySocketAddress = new InetSocketAddress("127.0.0.1", 0);
    private final List readPacketCache = Collections.synchronizedList(new ArrayList());
    private MemoryConnection pairedConnection;
    private NetHandler myNetHandler;

    /** set to true by {server,network}Shutdown */
    private boolean shuttingDown = false;
    private String shutdownReason = "";
    private Object[] field_74439_g;
    private boolean gamePaused = false;

    // ==== Begin modified code
    public static final PacketHooks packetHooksClient = new PacketHooks();
    static { packetHooksClient.load(); }
    public final boolean clientSide;
    // Because the code paths are completely different depending on whether the
    // user is disconnecting actively or passively, and can even overlap in
    // some scenarios, use a flag so we only dispatch one close event.
    private boolean sentCloseEvent;
    // ==== End modified code

    public MemoryConnection(NetHandler par1NetHandler) throws IOException
    {
        this.myNetHandler = par1NetHandler;
        // ==== Begin modified code
        // HACK: to minimize the number of classes we have to patch, examine the
        // stack trace to see who created this connection.
        clientSide = Thread.currentThread().getStackTrace()[2].getClassName().equals(NetClientHandler.class.getName());
        if (clientSide) {
            packetHooksClient.dispatchNewConnectionEvent(this);
        }
        // ==== End modified code
    }

    /**
     * Sets the NetHandler for this NetworkManager. Server-only.
     */
    public void setNetHandler(NetHandler par1NetHandler)
    {
        this.myNetHandler = par1NetHandler;
    }

    /**
     * Adds the packet to the correct send queue (chunk data packets go to a separate queue).
     */
    public void addToSendQueue(Packet par1Packet)
    {
        if (!this.shuttingDown)
        {
            // ==== Begin modified code
            if (clientSide && packetHooksClient.dispatchPacketEvent(this, par1Packet, true)) {
                return;
            }
            // ==== End modified code
            this.pairedConnection.processOrCachePacket(par1Packet);
        }
    }

    public void closeConnections()
    {
        this.pairedConnection = null;
        this.myNetHandler = null;
    }

    public boolean isConnectionActive()
    {
        return !this.shuttingDown && this.pairedConnection != null;
    }

    /**
     * Wakes reader and writer threads
     */
    public void wakeThreads() {}

    /**
     * Checks timeouts and processes all pending read packets.
     */
    public void processReadPackets()
    {
        int var1 = 2500;

        while (var1-- >= 0 && !this.readPacketCache.isEmpty())
        {
            Packet var2 = (Packet)this.readPacketCache.remove(0);
            // ==== Begin modified code
            if (clientSide && packetHooksClient.dispatchPacketEvent(this, var2, false)) {
                continue;
            }
            // ==== End modified code
            var2.processPacket(this.myNetHandler);
        }

        if (this.readPacketCache.size() > var1)
        {
            System.out.println("Memory connection overburdened; after processing 2500 packets, we still have " + this.readPacketCache.size() + " to go!");
        }

        if (this.shuttingDown && this.readPacketCache.isEmpty())
        {
            this.myNetHandler.handleErrorMessage(this.shutdownReason, this.field_74439_g);
            // ==== Begin modified code
            packetHooksClient.dispatchForgeRemoteCloseConnectionEvent(this, myNetHandler);
            // ==== End modified code
        }
    }

    /**
     * Return the InetSocketAddress of the remote endpoint
     */
    public SocketAddress getSocketAddress()
    {
        return mySocketAddress;
    }

    /**
     * Shuts down the server. (Only actually used on the server)
     */
    public void serverShutdown()
    {
        this.shuttingDown = true;
        // ==== Begin modified code
        if (clientSide && !sentCloseEvent) {
            packetHooksClient.dispatchCloseConnectionEvent(this, "Quitting", new Object[] {});
            sentCloseEvent = true;
        }
        // ==== End modified code
    }

    /**
     * Shuts down the network with the specified reason. Closes all streams and sockets, spawns NetworkMasterThread to
     * stop reading and writing threads.
     */
    public void networkShutdown(String par1Str, Object ... par2ArrayOfObj)
    {
        this.shuttingDown = true;
        this.shutdownReason = par1Str;
        this.field_74439_g = par2ArrayOfObj;
        // ==== Begin modified code
        if (clientSide && !sentCloseEvent) {
            packetHooksClient.dispatchCloseConnectionEvent(this, par1Str, par2ArrayOfObj);
            sentCloseEvent = true;
        }
        // ==== End modified code
    }

    /**
     * returns 0 for memoryConnections
     */
    public int packetSize()
    {
        return 0;
    }

    public void pairWith(MemoryConnection par1MemoryConnection)
    {
        this.pairedConnection = par1MemoryConnection;
        par1MemoryConnection.pairedConnection = this;
    }

    public boolean isGamePaused()
    {
        return this.gamePaused;
    }

    public void setGamePaused(boolean par1)
    {
        this.gamePaused = par1;
    }

    public MemoryConnection getPairedConnection()
    {
        return this.pairedConnection;
    }

    /**
     * acts immiditally if isWritePacket, otherwise adds it to the readCache to be processed next tick
     */
    public void processOrCachePacket(Packet par1Packet)
    {
        String var2 = this.myNetHandler.isServerHandler() ? ">" : "<";

        if (par1Packet.isWritePacket() && this.myNetHandler.canProcessPackets())
        {
            // ==== Begin modified code
            if (clientSide && packetHooksClient.dispatchPacketEvent(this, par1Packet, false)) {
                return;
            }
            // ==== End modified code
            par1Packet.processPacket(this.myNetHandler);
        }
        else
        {
            this.readPacketCache.add(par1Packet);
        }
    }
}

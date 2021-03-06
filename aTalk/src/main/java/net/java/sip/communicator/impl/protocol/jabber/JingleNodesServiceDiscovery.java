/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.util.Logger;

import org.atalk.util.StringUtils;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.*;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.xmpp.jnodes.smack.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Search for jingle nodes.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class JingleNodesServiceDiscovery implements Runnable
{
	/**
	 * Logger of this class
	 */
	private static final Logger logger = Logger.getLogger(JingleNodesServiceDiscovery.class);

	/**
	 * Property containing jingle nodes prefix to search for.
	 */
	private static final String JINGLE_NODES_SEARCH_PREFIX_PROP = "protocol.jabber.JINGLE_NODES_SEARCH_PREFIXES";

	/**
	 * Property containing jingle nodes prefix to search for.
	 */
	private static final String JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP
			= "protocol.jabber.JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST";

	/**
	 * Synchronization object to monitor auto discovery.
	 */
	private final Object jingleNodesSyncRoot;

	/**
	 * The service.
	 */
	private final SmackServiceNode service;

	/**
	 * The connection, must be connected.
	 */
	private final XMPPTCPConnection connection;

	/**
	 * Our account.
	 */
	private final JabberAccountIDImpl accountID;

	/**
	 * Creates discovery
	 *
	 * @param service
	 * 		the service.
	 * @param connection
	 * 		the connected connection.
	 * @param accountID
	 * 		our account.
	 * @param syncRoot
	 * 		the synchronization object while discovering.
	 */
	JingleNodesServiceDiscovery(SmackServiceNode service, XMPPTCPConnection connection,
			JabberAccountIDImpl accountID, Object syncRoot)
	{
		this.jingleNodesSyncRoot = syncRoot;
		this.service = service;
		this.connection = connection;
		this.accountID = accountID;
	}

	/**
	 * The actual discovery.
	 */
	public void run()
	{
		synchronized (jingleNodesSyncRoot) {
			long start = System.currentTimeMillis();
			if (logger.isInfoEnabled()) {
				logger.info("Start Jingle Nodes discovery!");
			}

			SmackServiceNode.MappedNodes nodes;
			String searchNodesWithPrefix = JabberActivator.getResources().getSettingsString(
					JINGLE_NODES_SEARCH_PREFIX_PROP);
			if (searchNodesWithPrefix == null || searchNodesWithPrefix.length() == 0)
				searchNodesWithPrefix = JabberActivator.getConfigurationService().getString(JINGLE_NODES_SEARCH_PREFIX_PROP);

			// if there are no default prefix settings or this option is turned off, just process
			// with default service discovery making list empty.
			if (searchNodesWithPrefix == null || searchNodesWithPrefix.length() == 0
					|| searchNodesWithPrefix.equalsIgnoreCase("off")) {
				searchNodesWithPrefix = "";
			}

			nodes = searchServicesWithPrefix(service, connection, 6, 3, 20, JingleChannelIQ.UDP,
					accountID.isJingleNodesSearchBuddiesEnabled(),
					accountID.isJingleNodesAutoDiscoveryEnabled(), searchNodesWithPrefix);

			if (logger.isInfoEnabled()) {
				logger.info("End of Jingle Nodes discovery!");
				logger.info("Found " + (nodes != null ? nodes.getRelayEntries().size() : "0")
						+ " Jingle Nodes relay for account: " + accountID.getAccountJid()
						+ " in " + (System.currentTimeMillis() - start) + " ms.");
			}
			if (nodes != null)
				service.addEntries(nodes);
		}
	}

	/**
	 * Searches for services as the prefix list has priority. If it is set return after first found
	 * service.
	 *
	 * @param service
	 * 		the service.
	 * @param xmppConnection
	 * 		the connection.
	 * @param maxEntries
	 * 		maximum entries to be searched.
	 * @param maxDepth
	 * 		the depth while recursively searching.
	 * @param maxSearchNodes
	 * 		number of nodes to query
	 * @param protocol
	 * 		the protocol
	 * @param searchBuddies
	 * 		should we search our buddies in contactlist.
	 * @param autoDiscover
	 * 		is auto discover turned on
	 * @param prefix
	 * 		the coma separated list of prefixes to be searched first.
	 * @return
	 */
	private SmackServiceNode.MappedNodes searchServicesWithPrefix(SmackServiceNode service,
			XMPPTCPConnection xmppConnection, int maxEntries, int maxDepth, int maxSearchNodes,
			String protocol, boolean searchBuddies, boolean autoDiscover, String prefix)
	{
		if (xmppConnection == null || !xmppConnection.isConnected()) {
			return null;
		}

		SmackServiceNode.MappedNodes mappedNodes = new SmackServiceNode.MappedNodes();
		ConcurrentHashMap<Jid, Jid> visited = new ConcurrentHashMap<>();

		// Request to our pre-configured trackerEntries
		for (Map.Entry<Jid, TrackerEntry> entry : service.getTrackerEntries().entrySet()) {
			SmackServiceNode.deepSearch(xmppConnection, maxEntries, entry.getValue().getJid(),
					mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
		}

		if (autoDiscover) {
			boolean continueSearch = searchDiscoItems(service, xmppConnection, maxEntries,
					xmppConnection.getServiceName(), mappedNodes, maxDepth - 1, maxSearchNodes,
					protocol, visited, prefix);

			// option to stop after first found is turned on, lets exit
			if (continueSearch) {
				// Request to Server
				try {
					SmackServiceNode.deepSearch(xmppConnection, maxEntries,
							JidCreate.from(xmppConnection.getHost()),
							mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
				}
				catch (XmppStringprepException e) {
					e.printStackTrace();
				}

				// Request to Buddies
				Roster roster = Roster.getInstanceFor(xmppConnection);
				if ((roster != null) && searchBuddies) {
					for (final RosterEntry re : roster.getEntries()) {
						final List<Presence> i = roster.getPresences(re.getJid());
						for (final Presence presence : i) {
							if (presence.isAvailable()) {
								SmackServiceNode.deepSearch(xmppConnection, maxEntries,
										presence.getFrom(), mappedNodes, maxDepth - 1,
										maxSearchNodes,
										protocol, visited);
							}
						}
					}
				}
			}
		}
		return mappedNodes;
	}

	/**
	 * Discover services and query them.
	 *
	 * @param service
	 * 		the service.
	 * @param xmppConnection
	 * 		the connection.
	 * @param maxEntries
	 * 		maximum entries to be searched.
	 * @param startPoint
	 * 		the start point to search recursively
	 * @param mappedNodes
	 * 		nodes found
	 * @param maxDepth
	 * 		the depth while recursively searching.
	 * @param maxSearchNodes
	 * 		number of nodes to query
	 * @param protocol
	 * 		the protocol
	 * @param visited
	 * 		nodes already visited
	 * @param prefix
	 * 		the coma separated list of prefixes to be searched first.
	 * @return
	 */
	private static boolean searchDiscoItems(SmackServiceNode service,
			XMPPTCPConnection xmppConnection, int maxEntries, Jid startPoint,
			SmackServiceNode.MappedNodes mappedNodes, int maxDepth, int maxSearchNodes,
			String protocol, ConcurrentHashMap<Jid, Jid> visited, String prefix)
	{
		String[] prefixes = prefix.split(",");

		// default is to stop when first one is found
		boolean stopOnFirst = true;

		String stopOnFirstDefaultValue = JabberActivator.getResources()
				.getSettingsString(JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP);
		if (stopOnFirstDefaultValue != null) {
			stopOnFirst = Boolean.parseBoolean(stopOnFirstDefaultValue);
		}
		stopOnFirst = JabberActivator.getConfigurationService()
				.getBoolean(JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP, stopOnFirst);

		final DiscoverItems items = new DiscoverItems();
		items.setTo(startPoint);
		StanzaCollector collector
				= xmppConnection.createStanzaCollector(new StanzaIdFilter(items.getStanzaId()));
		try {
			xmppConnection.sendStanza(items);
		}
		catch (NotConnectedException | InterruptedException e) {
			e.printStackTrace();
		}

		DiscoverItems result = null;
		try {
			result = collector.nextResult(
					Math.round(SmackConfiguration.getDefaultPacketReplyTimeout() * 1.5));
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (result != null) {
			// first search priority items
			List<DiscoverItems.Item> i = result.getItems();
			for (DiscoverItems.Item item : i) {
				if (item != null) {
					for (String pref : prefixes) {
						if (!StringUtils.isNullOrEmpty(pref)
								&& item.getEntityID().toString().startsWith(pref.trim())) {
							SmackServiceNode.deepSearch(xmppConnection, maxEntries,
									item.getEntityID(), mappedNodes, maxDepth, maxSearchNodes,
									protocol, visited);

							if (stopOnFirst)
								return false;// stop and don't continue
						}
					}
				}
			}
			// now search rest
			i = result.getItems();
			for (DiscoverItems.Item item : i) {
				if (item != null) {
					// we may searched already this node if it starts with some of the prefixes
					if (!visited.containsKey(item.getEntityID()))
						SmackServiceNode.deepSearch(xmppConnection, maxEntries, item.getEntityID(),
								mappedNodes, maxDepth, maxSearchNodes, protocol, visited);

					if (stopOnFirst)
						return false;// stop and don't continue
				}
			}
		}
		collector.cancel();
		// true we should continue searching
		return true;
	}
}

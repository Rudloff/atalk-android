/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.WebSocketPacketExtension;

import org.jivesoftware.smack.packet.ExtensionElement;

import java.util.*;

/**
 * An {@link AbstractPacketExtension} implementation for transport elements.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class IceUdpTransportPacketExtension extends AbstractPacketExtension
{
	/**
	 * The name of the "transport" element.
	 */
	public static final String NAMESPACE = "urn:xmpp:jingle:transports:ice-udp:1";

	/**
	 * The name of the "transport" element.
	 */
	public static final String ELEMENT_NAME = "transport";

	/**
	 * The name of the <tt>pwd</tt> ICE attribute.
	 */
	public static final String PWD_ATTR_NAME = "pwd";

	/**
	 * The name of the <tt>ufrag</tt> ICE attribute.
	 */
	public static final String UFRAG_ATTR_NAME = "ufrag";

	/**
	 * A list of one or more candidates representing each of the initiator's higher-priority
	 * transport candidates as determined in accordance with the ICE methodology.
	 */
	private final List<CandidatePacketExtension> candidateList = new ArrayList<>();

	/**
	 * Once the parties have connectivity and therefore the initiator has completed ICE as
	 * explained in RFC 5245, the initiator MAY communicate the in-use candidate pair in the
	 * signalling channel by sending a transport-info message that contains a "remote-candidate"
	 * element
	 */
	private RemoteCandidatePacketExtension remoteCandidate;

	/**
	 * Creates a new {@link IceUdpTransportPacketExtension} instance.
	 */
	public IceUdpTransportPacketExtension()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}

	/**
	 * Creates a new {@link IceUdpTransportPacketExtension} instance with the specified
	 * <tt>namespace</tt> and <tt>elementName</tt>. The purpose of this method is to allow
	 * {@link RawUdpTransportPacketExtension} to extend this class.
	 *
	 * @param namespace
	 * 		the XML namespace that the instance should belong to.
	 * @param elementName
	 * 		the name of the element that we would be representing.
	 */
	protected IceUdpTransportPacketExtension(String namespace, String elementName)
	{
		super(elementName, namespace);
	}

	/**
	 * Sets the ICE defined password attribute.
	 *
	 * @param pwd
	 * 		a password <tt>String</tt> as defined in RFC 5245
	 */
	public void setPassword(String pwd)
	{
		super.setAttribute(PWD_ATTR_NAME, pwd);
	}

	/**
	 * Returns the ICE defined password attribute.
	 *
	 * @return a password <tt>String</tt> as defined in RFC 5245
	 */
	public String getPassword()
	{
		return super.getAttributeAsString(PWD_ATTR_NAME);
	}

	/**
	 * Sets the ICE defined user fragment attribute.
	 *
	 * @param ufrag
	 * 		a user fragment <tt>String</tt> as defined in RFC 5245
	 */
	public void setUfrag(String ufrag)
	{
		super.setAttribute(UFRAG_ATTR_NAME, ufrag);
	}

	/**
	 * Returns the ICE defined user fragment attribute.
	 *
	 * @return a user fragment <tt>String</tt> as defined in RFC 5245
	 */
	public String getUfrag()
	{
		return super.getAttributeAsString(UFRAG_ATTR_NAME);
	}

	/**
	 * Returns this element's child (local or remote) candidate elements.
	 *
	 * @return this element's child (local or remote) candidate elements.
	 */
	@Override
	public List<? extends ExtensionElement> getChildExtensions()
	{
		List<ExtensionElement> childExtensions = new ArrayList<>();
		List<? extends ExtensionElement> superChildExtensions = super.getChildExtensions();
		childExtensions.addAll(superChildExtensions);

		synchronized (candidateList) {
			if (candidateList.size() > 0)
				childExtensions.addAll(candidateList);
			else if (remoteCandidate != null)
				childExtensions.add(remoteCandidate);
		}
		return childExtensions;
	}

	/**
	 * Adds <tt>candidate</tt> to the list of {@link CandidatePacketExtension}s registered with
	 * this transport.
	 *
	 * @param candidate
	 * 		the new {@link CandidatePacketExtension} to add to this transport element.
	 */
	public void addCandidate(CandidatePacketExtension candidate)
	{
		synchronized (candidateList) {
			candidateList.add(candidate);
		}
	}

	/**
	 * Removes <tt>candidate</tt> from the list of {@link CandidatePacketExtension}s registered
	 * with this transport.
	 *
	 * @param candidate
	 * 		the <tt>CandidatePacketExtension</tt> to remove from this transport element
	 *
	 * @return <tt>true</tt> if the list of <tt>CandidatePacketExtension</tt>s registered with this
	 * transport contained the specified <tt>candidate</tt>
	 */
	public boolean removeCandidate(CandidatePacketExtension candidate)
	{
		synchronized (candidateList) {
			return candidateList.remove(candidate);
		}
	}

	/**
	 * Removes given <tt>PacketExtension</tt> from the list of child packet extensions.
	 * <tt>CandidatePacketExtension</tt> are not taken into account in this method and
	 * {@link #removeCandidate(CandidatePacketExtension)} should be used instead.
	 *
	 * @param childExtension
	 * 		<tt>ExtensionElement</tt> instance to be removed from child ExtensionElement list.
	 *
	 * @return <tt>true</tt> if given <tt>childExtension</tt> has been in the list and was
	 * removed or <tt>false</tt> otherwise.
	 */
	public boolean removeChildExtension(ExtensionElement childExtension)
	{
		List<? extends ExtensionElement> childExtensions = super.getChildExtensions();

		return childExtensions != null
				&& childExtensions.remove(childExtension);
	}

	/**
	 * Returns the list of {@link CandidatePacketExtension}s currently registered with this
	 * transport.
	 *
	 * @return the list of {@link CandidatePacketExtension}s currently registered with this
	 * transport.
	 */
	public List<CandidatePacketExtension> getCandidateList()
	{
		synchronized (candidateList) {
			return new ArrayList<>(candidateList);
		}
	}

	/**
	 * Sets <tt>candidate</tt> as the in-use candidate after ICE has terminated.
	 *
	 * @param candidate
	 * 		the new {@link CandidatePacketExtension} to set as an in-use candidate for this
	 * 		session.
	 */
	public void setRemoteCandidate(RemoteCandidatePacketExtension candidate)
	{
		this.remoteCandidate = candidate;
	}

	/**
	 * Returns the in-use <tt>candidate</tt> for this session.
	 *
	 * @return Returns the in-use <tt>candidate</tt> for this session.
	 */
	public RemoteCandidatePacketExtension getRemoteCandidate()
	{
		return remoteCandidate;
	}

	/**
	 * Tries to determine whether <tt>childExtension</tt> is a {@link CandidatePacketExtension}, a
	 * {@link RemoteCandidatePacketExtension} or something else and then adds it as such.
	 *
	 * @param childExtension
	 * 		the extension we'd like to add here.
	 */
	@Override
	public void addChildExtension(ExtensionElement childExtension)
	{
		// first check for RemoteCandidate because they extend Candidate.
		if (childExtension instanceof RemoteCandidatePacketExtension)
			setRemoteCandidate((RemoteCandidatePacketExtension) childExtension);

		else if (childExtension instanceof CandidatePacketExtension)
			addCandidate((CandidatePacketExtension) childExtension);

		else
			super.addChildExtension(childExtension);
	}

	/**
	 * Checks whether an 'rtcp-mux' extension has been added to this
	 * <tt>IceUdpTransportPacketExtension</tt>.
	 *
	 * @return <tt>true</tt> if this <tt>IceUdpTransportPacketExtension</tt> has a child with the
	 * 'rtcp-mux' name.
	 */
	public boolean isRtcpMux()
	{
		for (ExtensionElement packetExtension : getChildExtensions()) {
			if (RtcpmuxPacketExtension.ELEMENT_NAME.equals(packetExtension.getElementName()))
				return true;
		}
		return false;
	}

	/**
	 * Clones a specific <tt>IceUdpTransportPacketExtension</tt> and its candidates.
	 *
	 * @param src
	 * 		the <tt>IceUdpTransportPacketExtension</tt> to be cloned
	 * @return a new <tt>IceUdpTransportPacketExtension</tt> instance which has the same run-time
	 * type, attributes, namespace, text and candidates as the specified <tt>src</tt>
	 */
	public static IceUdpTransportPacketExtension cloneTransportAndCandidates(
			IceUdpTransportPacketExtension src)
	{
		return cloneTransportAndCandidates(src, false);
	}

	/**
	 * Clones a specific <tt>IceUdpTransportPacketExtension</tt> and its candidates.
	 *
	 * @param src
	 * 		the <tt>IceUdpTransportPacketExtension</tt> to be cloned
	 * @param copyDtls
	 * 		if <tt>true</tt> will also copy {@link DtlsFingerprintPacketExtension}.
	 * @return a new <tt>IceUdpTransportPacketExtension</tt> instance which has the same run-time
	 * type, attributes, namespace, text and candidates as the specified <tt>src</tt>
	 *
	 */
	public static IceUdpTransportPacketExtension cloneTransportAndCandidates(
			IceUdpTransportPacketExtension src, boolean copyDtls)
	{
		IceUdpTransportPacketExtension dst = null;
		if (src != null) {
			dst = AbstractPacketExtension.clone(src);
			// Copy candidates
			for (CandidatePacketExtension srcCand : src.getCandidateList()) {
				if (!(srcCand instanceof RemoteCandidatePacketExtension))
					dst.addCandidate(AbstractPacketExtension.clone(srcCand));
			}
			// Copy "web-socket" extensions.
			for (WebSocketPacketExtension wspe : src.getChildExtensionsOfType(
					WebSocketPacketExtension.class)) {
				dst.addChildExtension(new WebSocketPacketExtension(wspe.getUrl()));
			}
			// Copy RTCP MUX
			if (src.isRtcpMux()) {
				dst.addChildExtension(new RtcpmuxPacketExtension());
			}
			// Optionally copy DTLS
			if (copyDtls) {
				for (DtlsFingerprintPacketExtension dtlsFingerprint
						: src.getChildExtensionsOfType(DtlsFingerprintPacketExtension.class)) {
					DtlsFingerprintPacketExtension copy = new DtlsFingerprintPacketExtension();

					copy.setFingerprint(dtlsFingerprint.getFingerprint());
					copy.setHash(dtlsFingerprint.getHash());
					copy.setRequired(dtlsFingerprint.getRequired());
					copy.setSetup(dtlsFingerprint.getSetup());

					dst.addChildExtension(copy);
				}
			}
		}
		return dst;
	}
}

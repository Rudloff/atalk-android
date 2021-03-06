/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.jabber.JabberChatRoomMember;

import org.jivesoftware.smackx.muc.Occupant;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;

/**
 * A Jabber implementation of the chat room member.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class ChatRoomMemberJabberImpl implements JabberChatRoomMember
{
    /**
     * The chat room that we are a member of.
     */
    private final ChatRoomJabberImpl chatRoom;

    /**
     * The role that this member has in its member room.
     */
    private ChatRoomMemberRole role;

    /**
     * The jabber id of the member (will only be visible to members with necessary permissions)
     * can either be BareJid or NickName
     */
    private final Jid jabberID;

    /**
     * The nick name that this member is using inside its containing chat room.
     */
    private Resourcepart nickName;

    /**
     * The email that this member is using inside its containing chat room.
     */
    private String email;

    /**
     * The URL of the avatar of this member.
     */
    private String avatarUrl;

    /**
     * The mContact from our server stored mContact list corresponding to this member.
     */
    private Contact mContact;

    /**
     * The avatar of this chat room member.
     */
    private byte[] avatar;

    /**
     * The display name of this {@link ChatRoomMember}.
     */
    private String displayName;

    private OperationSetPersistentPresenceJabberImpl presenceOpSet = null;

    /**
     * Creates a jabber chat room member with the specified containing chat room parent.
     *
     * @param chatRoom the room that this <tt>ChatRoomMemberJabberImpl</tt> is a member of.
     * @param nickName the nick name that the member is using to participate in the chat room
     * @param jabberID the jabber id, if available, of the member or null otherwise.
     */
    public ChatRoomMemberJabberImpl(ChatRoomJabberImpl chatRoom, Resourcepart nickName, Jid jabberID)
    {
        this.jabberID = jabberID;
        this.nickName = nickName;
        this.chatRoom = chatRoom;

        presenceOpSet = (OperationSetPersistentPresenceJabberImpl)
                chatRoom.getParentProvider().getOperationSet(OperationSetPersistentPresence.class);

        mContact = presenceOpSet.findContactByID(jabberID.asBareJid());

        // If we have found a mContact we set also its avatar.
        if (mContact != null) {
            this.avatar = mContact.getImage();
        }
        // just query the stack for role, if its present will be set
        getRole();
    }

    /**
     * Returns the chat room that this member is participating in.
     *
     * @return the <tt>ChatRoom</tt> instance that this member belongs to.
     */
    public ChatRoom getChatRoom()
    {
        return chatRoom;
    }

    /**
     * Returns the jabber id of the member; can either be BareJid or reserved nick.
     *
     * @return the jabber id.
     */
    public Jid getJabberID()
    {
        return jabberID;
    }

    /**
     * Returns the mContact identifier representing this mContact.
     *
     * @return a String (mContact address), uniquely representing the mContact over the service the
     * service being used by the associated protocol provider instance
     */
    public String getContactAddress()
    {
        return (jabberID != null) ? jabberID.toString() : nickName.toString();
    }

    /**
     * Returns the name of this member as it is known in its containing chatRoom (aka a nickname).
     *
     * @return the name of this member as it is known in the containing chat room (aka a nickname).
     */
    public String getNickName()
    {
        return nickName.toString();
    }

    public Resourcepart getNickNameAsResourcepart()
    {
        return nickName;
    }

    /**
     * Update the name of this participant
     *
     * @param newNick the newNick of the participant
     */
    protected void setNickName(Resourcepart newNick)
    {
        if ((newNick == null) || (newNick.length() == 0))
            throw new IllegalArgumentException("a room member nickname could not be null");
        nickName = newNick;
    }

    /**
     * Returns the protocol provider instance that this member has originated in.
     *
     * @return the <tt>ProtocolProviderService</tt> instance that created this member and its
     * containing cht room
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return chatRoom.getParentProvider();
    }

    /**
     * Returns the role of this chat room member in its containing room.
     *
     * @return a <tt>ChatRoomMemberRole</tt> instance indicating the role the this member in its
     * containing chat room.
     */
    public ChatRoomMemberRole getRole()
    {
        if (role == null) {
            EntityFullJid roleJid = null;
            roleJid = JidCreate.entityFullFrom(chatRoom.getIdentifier(), nickName);

            Occupant o = chatRoom.getMultiUserChat().getOccupant(roleJid);
            if (o == null) {
                return ChatRoomMemberRole.GUEST;
            }
            else
                role = ChatRoomJabberImpl.smackRoleToScRole(o.getRole(), o.getAffiliation());
        }
        return role;
    }

    /**
     * Returns the current role without trying to query it in the stack. Mostly used for event
     * creating on member role change.
     *
     * @return the current role of this member.
     */
    ChatRoomMemberRole getCurrentRole()
    {
        return this.role;
    }

    /**
     * Sets the role of this member.
     *
     * @param role the role to set
     */
    public void setRole(ChatRoomMemberRole role)
    {
        this.role = role;
    }

    /**
     * Returns the avatar of this member, that can be used when including it in user interface.
     *
     * @return an avatar (e.g. user photo) of this member.
     */
    public byte[] getAvatar()
    {
        return avatar;
    }

    /**
     * Sets the avatar for this member.
     *
     * @param avatar the avatar to set.
     */
    public void setAvatar(byte[] avatar)
    {
        this.avatar = avatar;
    }

    /**
     * Returns the protocol mContact corresponding to this member in our mContact list. The mContact
     * returned here could be used by the user interface to check if this member is contained in our
     * mContact list and in function of this to show additional information add additional
     * functionality.
     * Note: Use nick to retrieve mContact if null to take care the old history messages;
     *
     * @return the protocol mContact corresponding to this member in our mContact list.
     */
    public Contact getContact()
    {
        // old history muc message has mContact field = null (not stored);
        if ((mContact == null) && (presenceOpSet != null)) {
            mContact = presenceOpSet.findContactByID(getNickName());
        }
        return mContact;
    }

    /**
     * Sets the given mContact to this member.
     *
     * @param contact the mContact to set.
     */
    public void setContact(Contact contact)
    {
        mContact = contact;
    }

    @Override
    public PresenceStatus getPresenceStatus()
    {
        return mContact.getPresenceStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * Sets the display name of this {@link ChatRoomMember}.
     *
     * @param displayName the display name to set.
     */
    void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    /**
     * Return the email of this {@link ChatRoomMember}.
     *
     * @return the email of this {@link ChatRoomMember}.
     */
    public String getEmail()
    {
        return this.email;
    }

    /**
     * Sets the email of this {@link ChatRoomMember}.
     *
     * @param email the display name to set.
     */
    void setEmail(String email)
    {
        this.email = email;
    }

    /**
     * @return the URL of the avatar of this {@link ChatRoomMember}.
     */
    public String getAvatarUrl()
    {
        return this.avatarUrl;
    }

    /**
     * Sets the avatar URL of this {@link ChatRoomMember}.
     *
     * @param avatarUrl the value to set.
     */
    void setAvatarUrl(String avatarUrl)
    {
        this.avatarUrl = avatarUrl;
    }
}

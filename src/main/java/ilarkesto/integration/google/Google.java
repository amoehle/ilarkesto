/*
 * Copyright 2011 Witoslaw Koczewsi <wi@koczewski.de>, Artjom Kochtchi
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package ilarkesto.integration.google;

import ilarkesto.auth.LoginData;
import ilarkesto.auth.LoginDataProvider;
import ilarkesto.base.Proc;
import ilarkesto.base.Str;
import ilarkesto.base.time.Date;
import ilarkesto.core.base.Utl;
import ilarkesto.core.logging.Log;
import ilarkesto.integration.jdom.JDom;
import ilarkesto.io.IO;
import ilarkesto.swing.LoginPanel;

import java.io.File;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jdom.Document;
import org.jdom.Element;

import com.google.gdata.client.Service.GDataRequest;
import com.google.gdata.client.contacts.ContactQuery;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.BaseEntry;
import com.google.gdata.data.HtmlTextConstruct;
import com.google.gdata.data.Link;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.TextConstruct;
import com.google.gdata.data.ValueConstruct;
import com.google.gdata.data.contacts.Birthday;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.data.contacts.ContactGroupEntry;
import com.google.gdata.data.contacts.ContactGroupFeed;
import com.google.gdata.data.contacts.GroupMembershipInfo;
import com.google.gdata.data.contacts.Nickname;
import com.google.gdata.data.extensions.City;
import com.google.gdata.data.extensions.Country;
import com.google.gdata.data.extensions.Email;
import com.google.gdata.data.extensions.ExtendedProperty;
import com.google.gdata.data.extensions.FamilyName;
import com.google.gdata.data.extensions.FullName;
import com.google.gdata.data.extensions.GivenName;
import com.google.gdata.data.extensions.Im;
import com.google.gdata.data.extensions.Name;
import com.google.gdata.data.extensions.PhoneNumber;
import com.google.gdata.data.extensions.PostCode;
import com.google.gdata.data.extensions.Street;
import com.google.gdata.data.extensions.StructuredPostalAddress;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ContentType;

// http://code.google.com/apis/contacts/docs/2.0/developers_guide_java.html
// http://code.google.com/apis/buzz/v1/using_rest.html
public class Google {

	public static void main(String[] args) throws Throwable {
		// List<BuzzActivity> activities = getBuzzActivitiesConsumption();
		// System.out.println(Str.format(activities));

		// for (BuzzActivity buzz : getBuzzActivitiesConsumption(login)) {
		// System.out.println(buzz);
		// }

		LoginData login = LoginPanel.showDialog(null, "Google login", new File("runtimedata/google-login.properties"));
		if (login == null) return;
		String email = login.getLogin();
		ContactsService service = createContactsService(login, "Test");
		ContactGroupEntry testgroup = getContactGroupByTitle("testgroup", service, email);
		List<ContactEntry> contacts = getContacts(service, testgroup, email);
		ContactEntry contact = contacts.get(0);

		Log.DEBUG(getEmails(contact));
		Log.DEBUG(contact.getStructuredPostalAddresses().get(0).getCity());
		for (Im im : contact.getImAddresses()) {
			Log.DEBUG("--->", im.getAddress(), "|", im.getProtocol(), "|", im.getRel());
		}

		setAddress(contact, "Testadresse", "Teststrasse 12", "12345", "Teststadt", "DE", AddressRel.HOME, false);
		setInstantMessaging(contact, "olga@koczewski.de", ImProtocol.JABBER, ImRel.HOME);
		save(contact, service);

		// ContactGroupEntry group = getContactGroupByTitle("testgroup", service, login.getLogin());
		// if (group == null) {
		// group = createContactGroup("testgroup", service, login.getLogin());
		// }
		//
		// createContact(createPersonName("Duke", "Nukem"), group, service, login.getLogin());
		//
		// getContacts(service, group, login.getLogin());
	}

	private static Log log = Log.get(Google.class);

	public static enum ImProtocol {

		AIM("http://schemas.google.com/g/2005#AIM"), MSN("http://schemas.google.com/g/2005#MSN"), YAHOO(
				"http://schemas.google.com/g/2005#YAHOO"), SKYPE("http://schemas.google.com/g/2005#SKYPE"), QQ(
				"http://schemas.google.com/g/2005#QQ"), GOOGLE_TALK("http://schemas.google.com/g/2005#GOOGLE_TALK"), ICQ(
				"http://schemas.google.com/g/2005#ICQ"), JABBER("http://schemas.google.com/g/2005#JABBER");

		String href;

		private ImProtocol(String href) {
			this.href = href;
		}
	}

	public static enum ImRel {
		HOME("http://schemas.google.com/g/2005#home"), WORK("http://schemas.google.com/g/2005#work"), OTHER(
				"http://schemas.google.com/g/2005#other");

		String href;

		private ImRel(String href) {
			this.href = href;
		}
	}

	public static enum EmailRel {
		HOME("http://schemas.google.com/g/2005#home"), WORK("http://schemas.google.com/g/2005#work"), OTHER(
				"http://schemas.google.com/g/2005#other");

		String href;

		private EmailRel(String href) {
			this.href = href;
		}
	}

	public static enum AddressRel {
		HOME("http://schemas.google.com/g/2005#home"), WORK("http://schemas.google.com/g/2005#work"), OTHER(
				"http://schemas.google.com/g/2005#other");

		String href;

		private AddressRel(String href) {
			this.href = href;
		}
	}

	public static enum PhoneRel {
		HOME("http://schemas.google.com/g/2005#home"), WORK("http://schemas.google.com/g/2005#work"), FAX(
				"http://schemas.google.com/g/2005#fax"), HOME_FAX("http://schemas.google.com/g/2005#home_fax"), WORK_FAX(
				"http://schemas.google.com/g/2005#work_fax"), MOBILE("http://schemas.google.com/g/2005#mobile"), PAGER(
				"http://schemas.google.com/g/2005#pager"), OTHER("http://schemas.google.com/g/2005#other");

		String href;

		private PhoneRel(String href) {
			this.href = href;
		}
	}

	public static String weatherInfo(ilarkesto.core.time.Date date, String language, String location) {
		if (date.isPast()) return null;
		int inDays = date.getPeriodTo(ilarkesto.core.time.Date.today()).abs().toDays();
		if (inDays > 4) return null;

		Document doc = weatherAsXml(language, location);
		Element eReply = doc.getRootElement();
		Element eWeather = eReply.getChild("weather");
		if (eWeather == null) return null;

		if (date.isToday()) {
			Element eConditions = eWeather.getChild("current_conditions");
			if (eConditions == null) return null;
			String temp = JDom.getChildAttributeValue(eConditions, "temp_c", "data");
			String condition = JDom.getChildAttributeValue(eConditions, "condition", "data");
			String humidity = JDom.getChildAttributeValue(eConditions, "humidity", "data");
			String wind = JDom.getChildAttributeValue(eConditions, "wind_condition", "data");
			StringBuilder sb = new StringBuilder();
			sb.append(temp).append("°, ").append(condition);
			if (humidity != null) sb.append(", ").append(humidity);
			if (wind != null) sb.append(", ").append(wind);
			return sb.toString();
		}

		List<Element> elConditions = JDom.getChildren(eWeather, "forecast_conditions");
		if (elConditions.isEmpty()) return null;
		if (elConditions.size() < inDays) return null;
		Element eConditions = elConditions.get(inDays - 1);

		return JDom.getChildAttributeValue(eConditions, "condition", "data");
	}

	public static Document weatherAsXml(String language, String location) {
		return JDom.createDocument(weatherAsXmlString(language, location));
	}

	public static String weatherAsXmlString(String language, String location) {
		return IO.downloadUrlToString("http://www.google.com/ig/api?hl=" + language + "&weather="
				+ Str.encodeUrlParameter(location));
	}

	public static String oacurl(String url) {
		return Proc.execute("/opt/oacurl/oacurl", url);
	}

	public static List<BuzzActivity> getBuzzActivitiesSelf() {
		return getBuzzActivities("@self");
	}

	public static List<BuzzActivity> getBuzzActivitiesConsumption() {
		return getBuzzActivities("@consumption");
	}

	public static List<BuzzActivity> getBuzzActivities(String tag) {
		String url = "https://www.googleapis.com/buzz/v1/activities/@me/" + tag + "?prettyPrint=true";
		log.info("Loading Buzz activities:", url);
		// String s = Oacurl.fetchString(url, null);
		String s = oacurl(url);
		log.info("   ", s);
		List<BuzzActivity> activities = BuzzActivity.parseActivities(s);
		return activities;
	}

	public static void uploadContactPhoto(ContactEntry contact, ContactsService service, String contentType,
			byte[] photoData) {
		Link photoLink = contact.getContactPhotoLink();
		try {
			URL photoUrl = new URL(photoLink.getHref());
			GDataRequest request = service.createRequest(GDataRequest.RequestType.UPDATE, photoUrl, new ContentType(
					contentType));
			request.setEtag(photoLink.getEtag());
			OutputStream requestStream = request.getRequestStream();
			requestStream.write(photoData);
			request.execute();
			log.info("Contact photo uploaded:", toString(contact));
		} catch (Throwable ex) {
			throw new RuntimeException("Uploading contact photo failed: " + toString(contact), ex);
		}
	}

	public static String toString(BaseEntry entry) {
		StringBuilder sb = new StringBuilder();
		sb.append(entry.getId());
		TextConstruct title = entry.getTitle();
		if (title != null) {
			sb.append(" (").append(title.getPlainText()).append(")");
		}
		return sb.toString();
	}

	public static void removeEmails(ContactEntry contact) {
		contact.removeExtension(Email.class);
	}

	public static void removePhones(ContactEntry contact) {
		contact.removeExtension(PhoneNumber.class);
	}

	public static void removeAddresses(ContactEntry contact) {
		contact.removeExtension(StructuredPostalAddress.class);
	}

	public static void removeInstantMessages(ContactEntry contact) {
		contact.removeExtension(Im.class);
	}

	public static void setAddress(ContactEntry contact, String label, String street, String postcode, String city,
			String countryCode, AddressRel rel, boolean primary) {
		for (StructuredPostalAddress a : contact.getStructuredPostalAddresses()) {
			if (Utl.equals(label, a.getLabel()) && equals(street, a.getStreet()) && equals(postcode, a.getPostcode())
					&& equals(city, a.getCity()) && equals(countryCode, a.getCountry()) && rel.href.equals(a.getRel())) {
				a.setPrimary(primary);
				return;
			}
		}
		contact.addStructuredPostalAddress(createPostalAddress(label, street, postcode, city, countryCode, rel, primary));
	}

	private static boolean equals(String countryCode, Country country) {
		if (country == null) return country == null;
		return Utl.equals(countryCode, country.getCode());
	}

	private static boolean equals(String value, ValueConstruct vc) {
		if (vc == null) return value == null;
		return Utl.equals(value, vc.getValue());
	}

	public static Im setIcq(ContactEntry contact, String address, ImRel rel) {
		return setInstantMessaging(contact, address, ImProtocol.ICQ, rel);
	}

	public static Im setMsn(ContactEntry contact, String address, ImRel rel) {
		return setInstantMessaging(contact, address, ImProtocol.MSN, rel);
	}

	public static Im setJabber(ContactEntry contact, String address, ImRel rel) {
		return setInstantMessaging(contact, address, ImProtocol.JABBER, rel);
	}

	public static Im setGoogleTalk(ContactEntry contact, String address, ImRel rel) {
		return setInstantMessaging(contact, address, ImProtocol.GOOGLE_TALK, rel);
	}

	public static Im setSkype(ContactEntry contact, String address, ImRel rel) {
		return setInstantMessaging(contact, address, ImProtocol.SKYPE, rel);
	}

	public static Im setInstantMessaging(ContactEntry contact, String address, ImProtocol protocol, ImRel rel) {
		address = address.toLowerCase();
		for (Im im : contact.getImAddresses()) {
			if (rel.href.equals(im.getRel()) && protocol.href.equals(im.getProtocol())
					&& address.equals(im.getAddress())) return im;
		}
		Im im = createInstantMessaging(address, protocol, rel);
		contact.addImAddress(im);
		return im;
	}

	public static PhoneNumber setPhone(ContactEntry contact, String phoneNumber, PhoneRel rel) {
		phoneNumber = phoneNumber.toLowerCase();
		for (PhoneNumber phone : contact.getPhoneNumbers()) {
			String number = phone.getPhoneNumber().toLowerCase();
			if (number.equals(phoneNumber) && rel.href.equals(phone.getRel())) {
				// number already exists
				return phone;
			}
		}
		PhoneNumber phone = createPhoneNumber(phoneNumber, rel);
		contact.addPhoneNumber(phone);
		return phone;
	}

	public static void setPrimaryPhone(ContactEntry contact, String phoneNumber, PhoneRel rel) {
		boolean updated = false;
		phoneNumber = phoneNumber.toLowerCase();
		for (PhoneNumber phone : contact.getPhoneNumbers()) {
			String number = phone.getPhoneNumber().toLowerCase();
			if (number.equals(phoneNumber) && rel.href.equals(phone.getRel())) {
				phone.setPrimary(true);
				updated = true;
			} else {
				phone.setPrimary(false);
			}
		}
		if (!updated) throw new RuntimeException("Phone '" + phoneNumber + "' not found in: " + toString(contact));
	}

	public static List<String> getEmails(ContactEntry contact) {
		List<String> ret = new ArrayList<String>();
		for (Email email : contact.getEmailAddresses()) {
			ret.add(email.getAddress().toLowerCase());
		}
		return ret;
	}

	public static void setEmail(ContactEntry contact, String emailAddress, EmailRel rel, boolean primary) {
		boolean updated = false;
		emailAddress = emailAddress.toLowerCase();
		for (Email email : contact.getEmailAddresses()) {
			String address = email.getAddress().toLowerCase();
			if (address.equals(emailAddress)) {
				email.setPrimary(primary);
				updated = true;
			} else if (primary) {
				email.setPrimary(false);
			}
		}
		if (updated) return;
		contact.addEmailAddress(createEmail(emailAddress, rel, primary));
	}

	public static void delete(BaseEntry entry) {
		try {
			entry.delete();
		} catch (Throwable ex) {
			throw new RuntimeException("Deleting failed: " + toString(entry), ex);
		}
	}

	public static <E extends BaseEntry> E save(E entry, ContactsService service) {
		URL editUrl;
		try {
			editUrl = new URL(entry.getEditLink().getHref());
			return service.update(editUrl, entry);
		} catch (Throwable ex) {
			throw new RuntimeException("Saving failed: " + toString(entry), ex);
		}
	}

	public static void setExtendedProperty(ContactEntry contact, String name, String value) {
		for (ExtendedProperty property : contact.getExtendedProperties()) {
			if (name.equals(property.getName())) {
				property.setValue(value);
				return;
			}
		}

		ExtendedProperty property = new ExtendedProperty();
		property.setName(name);
		property.setValue(value);
		contact.addExtendedProperty(property);
	}

	public static String getExtendedProperty(ContactEntry contact, String name) {
		for (ExtendedProperty property : contact.getExtendedProperties()) {
			if (name.equals(property.getName())) return property.getValue();
		}
		return null;
	}

	public static GroupMembershipInfo createContactGroupMembershipInfo(ContactGroupEntry group) {
		GroupMembershipInfo groupMembershipInfo = new GroupMembershipInfo(false, group.getId());
		return groupMembershipInfo;
	}

	public static ContactEntry createContact(String name, ContactGroupEntry group, ContactsService service, String email) {
		return createContact(createOrganizationName(name), group, service, email);
	}

	public static ContactEntry createContact(Name name, ContactGroupEntry group, ContactsService service, String email) {
		String title = name.getFullName().getValue();

		ContactEntry contact = new ContactEntry();
		contact.setTitle(new PlainTextConstruct(title));
		contact.setName(name);

		if (group != null) {
			GroupMembershipInfo membershipInfo = createContactGroupMembershipInfo(group);
			contact.addGroupMembershipInfo(membershipInfo);
		}

		try {
			contact = service.insert(getContactsFeedUrl(email), contact);
		} catch (Throwable ex) {
			throw new RuntimeException("Creating contact '" + title + "' for " + email + " failed.", ex);
		}
		log.info("Contact '" + title + "' created for " + email);
		return contact;
	}

	public static Name createOrganizationName(String organizationName) {
		Name name = new Name();
		FullName fullName = new FullName();
		fullName.setValue(organizationName);
		name.setFullName(fullName);
		return name;
	}

	public static Name createPersonName(String givenName, String familyName) {
		Name name = new Name();
		StringBuilder full = new StringBuilder();
		if (givenName != null) {
			name.setGivenName(new GivenName(givenName, null));
			full.append(givenName);
		}
		if (familyName != null) {
			name.setFamilyName(new FamilyName(familyName, null));
			if (full.length() > 0) full.append(" ");
			full.append(familyName);
		}
		FullName fullName = new FullName();
		fullName.setValue(full.toString());
		name.setFullName(fullName);
		return name;
	}

	public static StructuredPostalAddress createPostalAddress(String label, String street, String postcode,
			String city, String country, AddressRel rel, boolean primary) {
		StructuredPostalAddress a = new StructuredPostalAddress();
		a.setRel(rel.href);
		if (rel == AddressRel.OTHER) a.setLabel(label);
		a.setStreet(new Street(street));
		a.setPostcode(new PostCode(postcode));
		a.setCity(new City(city));
		a.setCountry(new Country(country, country));
		a.setPrimary(primary);
		return a;
	}

	public static Nickname createNickname(String name) {
		if (name == null) return null;
		return new Nickname(name);
	}

	public static Birthday createBirthday(Date date) {
		if (date == null) return null;
		return new Birthday(date.toString());
	}

	public static Email createEmail(String address, EmailRel rel, boolean primary) {
		Email email = new Email();
		email.setAddress(address);
		email.setRel(rel.href);
		email.setPrimary(primary);
		return email;
	}

	public static Im createInstantMessaging(String address, ImProtocol protocol, ImRel rel) {
		Im im = new Im();
		im.setRel(rel.href);
		im.setAddress(address);
		im.setProtocol(protocol.href);
		return im;
	}

	public static PhoneNumber createPhoneNumber(String number, PhoneRel rel) {
		PhoneNumber phoneNumber = new PhoneNumber();
		phoneNumber.setPhoneNumber(number);
		phoneNumber.setRel(rel.href);
		return phoneNumber;
	}

	public static ContactGroupEntry createContactGroup(String title, ContactsService service, String email) {
		ContactGroupEntry group = new ContactGroupEntry();
		group.setTitle(new PlainTextConstruct(title));
		try {
			group = service.insert(getContactGroupsFeedUrl(email), group);
		} catch (Throwable ex) {
			throw new RuntimeException("Creating contact group '" + title + "' for " + email + " failed.", ex);
		}
		log.info("Contact group '" + title + "' created for " + email);
		return group;
	}

	public static ContactGroupEntry getContactGroupByTitle(String title, ContactsService service, String email) {
		for (ContactGroupEntry group : getContactGroups(service, email)) {
			if (title.equals(group.getTitle().getPlainText())) return group;
		}
		return null;
	}

	public static List<ContactEntry> getContacts(ContactsService service, ContactGroupEntry group, String email) {
		log.info("Loading contacts for", email);

		ContactQuery query = new ContactQuery(getContactsFeedUrl(email));
		query.setMaxResults(Integer.MAX_VALUE);
		if (group != null) query.setGroup(group.getId());

		ContactFeed resultFeed;
		try {
			resultFeed = service.getFeed(query, ContactFeed.class);
		} catch (Throwable ex) {
			throw new RuntimeException("Loading contacts for " + email + " failed.", ex);
		}
		List<ContactEntry> ret = new ArrayList<ContactEntry>();
		List<ContactEntry> entries = resultFeed.getEntries();
		log.debug("   ", entries.size() + " contacts received.");
		for (int i = 0; i < entries.size(); i++) {
			ContactEntry contact = entries.get(i);
			log.debug("   ", contact.getId(), "->", contact.getTitle().getPlainText());
			ret.add(contact);
		}
		return ret;
	}

	public static List<ContactGroupEntry> getContactGroups(ContactsService service, String email) {
		log.info("Loading contact groups for", email);
		ContactGroupFeed resultFeed;
		try {
			resultFeed = service.getFeed(getContactGroupsFeedUrl(email), ContactGroupFeed.class);
		} catch (Throwable ex) {
			throw new RuntimeException("Loading contact groups for " + email + " failed.", ex);
		}
		List<ContactGroupEntry> ret = new ArrayList<ContactGroupEntry>();
		for (int i = 0; i < resultFeed.getEntries().size(); i++) {
			ContactGroupEntry group = resultFeed.getEntries().get(i);
			log.debug("   ", group.getId(), "->", group.getTitle().getPlainText());
			ret.add(group);
		}
		return ret;
	}

	public static URL getContactGroupsFeedUrl(String email) {
		return getFeedUrl("groups", email, "full");
	}

	public static URL getContactsFeedUrl(String email) {
		return getFeedUrl("contacts", email, "full");
	}

	public static URL getFeedUrl(String entity, String email, String feed) {
		try {
			return new URL("http://www.google.com/m8/feeds/" + entity + "/" + email + "/" + feed);
		} catch (MalformedURLException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static ContactsService createContactsService(LoginDataProvider login, String clientApplicationId) {
		LoginData loginData = login.getLoginData();
		ContactsService contactsService = new ContactsService(clientApplicationId);
		try {
			contactsService.setUserCredentials(loginData.getLogin(), loginData.getPassword());
		} catch (AuthenticationException ex) {
			throw new RuntimeException("Google authentication failed.", ex);
		}
		return contactsService;
	}

	public static TextConstruct textConstruct(String s) {
		if (s == null) return null;
		return s.startsWith("<html") ? new HtmlTextConstruct(s) : new PlainTextConstruct(s);
	}

	public static boolean isGoogleTalkAddress(String email) {
		if (Str.isBlank(email)) return false;
		email = email.trim().toLowerCase();
		if (email.endsWith("@googlemail.com")) return true;
		if (email.endsWith("@gmail.com")) return true;
		return false;
	}

}

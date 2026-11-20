package dev.foldterm.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import java.text.DateFormatSymbols

class ContactsRepository(private val ctx: Context) {
    fun granted() = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun load(): List<ContactSummary> {
        if (!granted()) return emptyList()
        val cr = ctx.contentResolver
        val companies = HashMap<Long, String>()
        val phones = HashMap<Long, String>()
        val emails = HashMap<Long, String>()
        runCatching {
            cr.query(
                Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, Organization.COMPANY),
                "${Data.MIMETYPE}=?", arrayOf(Organization.CONTENT_ITEM_TYPE), null,
            )?.use { c -> while (c.moveToNext()) c.getString(1)?.takeIf { it.isNotBlank() }?.let { companies.putIfAbsent(c.getLong(0), it) } }
            cr.query(
                Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER), null, null,
                "${Phone.IS_SUPER_PRIMARY} DESC",
            )?.use { c -> while (c.moveToNext()) c.getString(1)?.let { phones.putIfAbsent(c.getLong(0), it) } }
            cr.query(
                Email.CONTENT_URI, arrayOf(Email.CONTACT_ID, Email.ADDRESS), null, null,
                "${Email.IS_SUPER_PRIMARY} DESC",
            )?.use { c -> while (c.moveToNext()) c.getString(1)?.let { emails.putIfAbsent(c.getLong(0), it) } }
        }
        val out = mutableListOf<ContactSummary>()
        runCatching {
            cr.query(
                Contacts.CONTENT_URI,
                arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED),
                "${Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL", null,
                "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += ContactSummary(
                        id = id,
                        lookupKey = c.getString(1) ?: "",
                        name = c.getString(2) ?: "?",
                        starred = c.getInt(3) == 1,
                        company = companies[id],
                        phone = phones[id],
                        email = emails[id],
                    )
                }
            }
        }
        return out
    }

    fun detail(s: ContactSummary): ContactDetail {
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        var company: String? = null
        var jobTitle: String? = null
        var birthday: String? = null
        var note: String? = null
        val accounts = mutableListOf<String>()
        val cr = ctx.contentResolver
        runCatching {
            cr.query(
                Data.CONTENT_URI, arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA4),
                "${Data.CONTACT_ID}=?", arrayOf(s.id.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val d1 = c.getString(1)?.trim().orEmpty()
                    if (d1.isEmpty()) continue
                    when (c.getString(0)) {
                        Phone.CONTENT_ITEM_TYPE -> if (d1 !in phones) phones += d1
                        Email.CONTENT_ITEM_TYPE -> if (d1 !in emails) emails += d1
                        StructuredPostal.CONTENT_ITEM_TYPE -> addresses += d1.replace("\n", ", ")
                        Organization.CONTENT_ITEM_TYPE -> { company = company ?: d1; jobTitle = jobTitle ?: c.getString(3) }
                        Event.CONTENT_ITEM_TYPE -> if (c.getInt(2) == Event.TYPE_BIRTHDAY) birthday = formatBirthday(d1)
                        Note.CONTENT_ITEM_TYPE -> note = d1
                    }
                }
            }
            cr.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE),
                "${RawContacts.CONTACT_ID}=?", arrayOf(s.id.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(0)
                    val label = when {
                        t == null -> "this phone"
                        t.contains("google") -> "Google"
                        t.contains("exchange") || t.contains("microsoft") || t.contains("outlook") -> "Microsoft"
                        t.contains("whatsapp") -> "WhatsApp"
                        t.contains("telegram") -> "Telegram"
                        t.contains("signal") -> "Signal"
                        else -> t.substringAfterLast('.')
                    }
                    if (label !in accounts) accounts += label
                }
            }
        }
        return ContactDetail(s, phones, emails, company, jobTitle, birthday, addresses, accounts, note)
    }

    private fun formatBirthday(raw: String): String {
        // formats: yyyy-MM-dd or --MM-dd
        val m = Regex("(\\d{4}|-)-(\\d{2})-(\\d{2})").find(raw) ?: return raw
        val month = m.groupValues[2].toIntOrNull() ?: return raw
        val day = m.groupValues[3].toIntOrNull() ?: return raw
        val name = DateFormatSymbols.getInstance().months.getOrNull(month - 1) ?: return raw
        return "$name $day"
    }

    fun contactUri(s: ContactSummary): Uri = Contacts.getLookupUri(s.id, s.lookupKey)
        ?: ContentUris.withAppendedId(Contacts.CONTENT_URI, s.id)

    fun viewIntent(s: ContactSummary) = Intent(Intent.ACTION_VIEW, contactUri(s))
    fun editIntent(s: ContactSummary) = Intent(Intent.ACTION_EDIT, contactUri(s))
    fun newIntent() = Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI)
    fun shareIntent(s: ContactSummary): Intent {
        val uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, s.lookupKey)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(Contacts.CONTENT_VCARD_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "share ${s.name}",
        )
    }
}

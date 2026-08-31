package com.kosd.log_inattendancesafeguard.services

import com.kosd.log_inattendancesafeguard.network.SupabaseClientProvider.client
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean

/**
 * Centralised legal text for Privacy Policy and Terms of Service.
 * Shared across ClockCard and Eventa — both apps use identical text
 * so a single public URL can serve both Play Store / App Store listings.
 * Versioned so consent records can be re-obtained when the policy changes.
 */
data class LegalDocument(
    val title: String,
    val version: String,
    val lastUpdated: String,
    val body: String
)

object LegalService {
    const val PRIVACY_POLICY_VERSION = "1.0"
    const val TERMS_OF_SERVICE_VERSION = "1.0"
    const val MONITORING_NOTICE_VERSION = "1.0"
    const val DPA_VERSION = "1.0"
    const val SUBPROCESSOR_DISCLOSURE_VERSION = "1.0"
    const val REFUND_POLICY_VERSION = "1.0"
    const val ACCESSIBILITY_STATEMENT_VERSION = "1.0"

    val privacyPolicy = LegalDocument(
        title = "Privacy Policy",
        version = PRIVACY_POLICY_VERSION,
        lastUpdated = "August 21, 2026",
        body = """
Last updated: August 21, 2026

This Privacy Policy describes how KOSD ("we", "us", or "our") collects, uses, and protects your personal information when you use our mobile applications, ClockCard and Eventa (the "Service"). By using the Service, you agree to the practices described in this policy.

1. INFORMATION WE COLLECT

1.1 Account Information
- Email address (used for authentication and account recovery)
- First name and last name (used for display within your organization)
- Encrypted password (managed by our authentication provider; we never see the plaintext)

1.2 Location Data
- When you check in or check out, we may collect your device's GPS coordinates at that moment in time.
- Location is only collected during an explicit check-in or check-out action. We do NOT track your location continuously or in the background.
- Location data is used solely to verify that you are within the geofence of your designated workplace, if your organization has enabled location verification.
- You may decline location permission. If you do, check-in/check-out will still work but will be marked as "location not verified."

1.3 Attendance Data
- Check-in and check-out timestamps
- Attendance status (present, late, absent, early departure, remote, on leave)
- Notes you optionally add to check-in/check-out records
- Late and early departure durations (calculated server-side)

1.4 Organization Data
- Your role within an organization (owner, admin, or member)
- The organization(s) you belong to
- Invite codes you create or use

1.5 Device and Usage Data
- App version and device model (for debugging and compatibility)
- Crash reports (if you opt in)

2. HOW WE USE YOUR INFORMATION

- To authenticate you and manage your account
- To record and report your attendance to your organization's administrators
- To verify your location at check-in/check-out (if enabled by your organization)
- To calculate attendance summaries and reports
- To send you service notifications (e.g., password reset emails)
- To comply with legal obligations

3. LAWFUL BASIS FOR PROCESSING (GDPR)

- Contract performance: Processing your attendance data is necessary to provide the Service you requested as part of your employment arrangement.
- Consent: Location data collection during check-in/check-out requires your explicit consent. You can withdraw consent at any time in Settings.
- Legitimate interests: We process aggregated, anonymized data for service improvement and security.

4. DATA SHARING

- Your organization's administrators (owners and admins) can view your attendance records, check-in/out times, and attendance status within their organization.
- We do NOT sell your personal data to third parties.
- We share data with our service providers (authentication, hosting) only as necessary to operate the Service, under data processing agreements.
- We may disclose data if required by law or to protect our legal rights.

5. DATA RETENTION

- Attendance records are retained for a period determined by your organization's data retention setting (default: 365 days).
- You may request deletion of your account and all associated data at any time via Settings > Delete Account.
- When you delete your account, your profile, attendance records, consent records, and organization memberships are permanently deleted within 30 days.

6. YOUR RIGHTS

- Access: You can request a copy of your personal data.
- Rectification: You can correct inaccurate data via Edit Profile.
- Erasure: You can delete your account and all data via Settings.
- Restriction: You can restrict certain processing by withdrawing consent.
- Portability: You can export your attendance data.
- Objection: You can object to certain processing activities.
- Withdrawal of consent: You can withdraw location consent at any time without affecting the legality of prior processing.

To exercise these rights, contact your organization administrator or email privacy@attendanceguard.app.

7. DATA SECURITY

- All data is encrypted in transit (TLS 1.2+) and at rest.
- Passwords are hashed by our authentication provider (bcrypt).
- Row-level security policies enforce that users can only access their own data and data within their organization.
- Biometric authentication is used as a local device gate. Your biometric data never leaves your device and is never sent to our servers.

8. CHILDREN'S PRIVACY

The Service is not directed to children under 16. We do not knowingly collect personal information from children. If you believe we have collected data from a child, please contact us.

9. INTERNATIONAL DATA TRANSFERS

Your data may be processed in the region where your Supabase instance is hosted. If you are in the EU/UK, your data is processed under Standard Contractual Clauses or an adequacy decision.

10. CHANGES TO THIS POLICY

We may update this Privacy Policy from time to time. We will notify you of material changes via the app or email. Continued use after changes constitutes acceptance.

11. CONTACT

For privacy questions or requests, contact: privacy@attendanceguard.app
        """.trimIndent()
    )

    val termsOfService = LegalDocument(
        title = "Terms of Service",
        version = TERMS_OF_SERVICE_VERSION,
        lastUpdated = "August 21, 2026",
        body = """
Last updated: August 21, 2026

These Terms of Service ("Terms") govern your use of the ClockCard and Eventa mobile applications ("Service") provided by KOSD. By creating an account or using the Service, you agree to these Terms.

1. ELIGIBILITY

You must be at least 16 years old and have the legal capacity to enter into these Terms. If you are using the Service as part of your employment, you represent that your employer has authorized you to do so.

2. ACCOUNTS

2.1 You are responsible for maintaining the security of your account and password.
2.2 You must provide accurate and complete information during registration.
2.3 You may not share your account credentials with others.
2.4 You can delete your account at any time via Settings > Delete Account.

3. ACCEPTABLE USE

You agree NOT to:
- Use the Service to submit false or fraudulent attendance records
- Attempt to circumvent location verification mechanisms
- Access another user's data without authorization
- Use the Service to harass, discriminate, or retaliate against employees
- Reverse engineer, decompile, or disassemble the app
- Use the Service in violation of applicable laws

4. ORGANIZATION MANAGEMENT

4.1 Organization owners and admins are responsible for configuring attendance rules, invite codes, and data retention settings.
4.2 Owners must inform their members that attendance and location data is being collected, and obtain necessary consents under applicable labor and privacy laws.
4.3 Owners are responsible for compliance with local employment monitoring regulations.

5. LOCATION AND MONITORING

5.1 The Service collects location data only during explicit check-in/check-out actions, not continuously.
5.2 Location collection requires your explicit consent, which you can withdraw at any time.
5.3 Your organization's administrators can view your attendance records and check-in/out times.
5.4 You will be shown a monitoring notice when you join an organization, explaining what data is collected and who can see it.

6. SUBSCRIPTIONS AND BILLING

6.1 The Service offers subscription tiers based on organization population size.
6.2 Subscriptions are billed through Apple App Store or Google Play Store, subject to their respective terms.
6.3 You can cancel a subscription at any time. Cancellation takes effect at the end of the current billing period.
6.4 Refunds are subject to the applicable store's refund policy.

7. DATA RETENTION AND DELETION

7.1 Attendance data is retained according to your organization's data retention setting.
7.2 You can request deletion of your account and data at any time.
7.3 Upon account deletion, your data is permanently removed within 30 days.

8. INTELLECTUAL PROPERTY

The Service, including its design, code, and content, is owned by KOSD and protected by intellectual property laws. You retain ownership of your personal data.

9. DISCLAIMER

The Service is provided "as is" without warranties of any kind. We do not guarantee that the Service will be uninterrupted, secure, or error-free.

10. LIMITATION OF LIABILITY

To the maximum extent permitted by law, KOSD shall not be liable for indirect, incidental, special, or consequential damages, including loss of profits, data, or business opportunities.

11. TERMINATION

We may suspend or terminate your account if you violate these Terms. You may terminate your account at any time via Settings.

12. GOVERNING LAW

These Terms are governed by the laws of your jurisdiction of residence, without regard to conflict of law principles.

13. CHANGES TO TERMS

We may update these Terms from time to time. We will notify you of material changes via the app or email. Continued use after changes constitutes acceptance.

14. CONTACT

For questions about these Terms, contact: legal@attendanceguard.app
        """.trimIndent()
    )

    val monitoringNotice = """
Employee Monitoring Notice

Your organization uses ClockCard or Eventa to track attendance. Here is what you need to know:

DATA COLLECTED:
- Your check-in and check-out times
- Your attendance status (present, late, early departure, etc.)
- Your GPS location at the moment of check-in/check-out (only if your organization has enabled location verification and you have given consent)
- Notes you optionally add to your attendance records

DATA NOT COLLECTED:
- Your location is NOT tracked continuously or in the background
- Your browsing history, app usage, or communications are NOT monitored
- Your device's camera, microphone, or contacts are NOT accessed

WHO CAN SEE YOUR DATA:
- Your organization's owners and administrators can view your attendance records, check-in/out times, and attendance status
- Other members cannot see your individual records
- Your data is not shared with third parties

YOUR RIGHTS:
- You can withdraw location consent at any time in Settings
- You can request a copy of your attendance data
- You can delete your account and all associated data at any time

DATA RETENTION:
- Your attendance data is retained according to your organization's data retention policy (default: 365 days)
- You can view the retention period in your organization's settings

By continuing to use ClockCard or Eventa as a member of this organization, you acknowledge that you have read and understood this notice.
    """.trimIndent()

    // ── Data Processing Addendum (DPA) ─────────────────────────────────

    val dataProcessingAddendum = LegalDocument(
        title = "Data Processing Addendum",
        version = DPA_VERSION,
        lastUpdated = "August 21, 2026",
        body = """
Last updated: August 21, 2026

This Data Processing Addendum ("DPA") forms part of the Terms of Service governing your use of the ClockCard and Eventa mobile applications (the "Service") provided by KOSD ("Processor"). This DPA reflects the parties' agreement with regard to the Processing of Personal Data as defined under the GDPR and other applicable data protection laws.

1. DEFINITIONS

1.1 "Controller" means the organization that determines the purposes and means of Processing Personal Data (i.e., your organization).
1.2 "Processor" means KOSD, which Processes Personal Data on behalf of the Controller.
1.3 "Personal Data" means any information relating to an identified or identifiable natural person, including names, email addresses, location data, and attendance records.
1.4 "Processing" means any operation performed on Personal Data, such as collection, recording, storage, retrieval, use, disclosure, and deletion.
1.5 "Subprocessor" means any third party engaged by the Processor to assist in Processing Personal Data.

2. SCOPE AND ROLES

2.1 The Controller is the organization using the Service to manage attendance. The Processor acts on the Controller's instructions as documented in the Service configuration (e.g., data retention settings, location verification toggles).
2.2 The categories of Personal Data Processed include: member names, email addresses, check-in/out timestamps, attendance status, GPS coordinates at check-in/out (if enabled), and consent records.
2.3 The purposes of Processing are limited to providing the Service: authentication, attendance recording, location verification, reporting, and account management.

3. PROCESSOR OBLIGATIONS

3.1 The Processor shall Process Personal Data only on documented instructions from the Controller, including with regard to transfers of Personal Data to a third country.
3.2 The Processor shall ensure that persons authorized to Process Personal Data have committed themselves to confidentiality.
3.3 The Processor shall implement appropriate technical and organizational measures to ensure a level of security appropriate to the risk, including:
    - Encryption of Personal Data in transit (TLS 1.2+) and at rest.
    - Row-level security policies restricting data access to the Controller's organization.
    - Hashed passwords (bcrypt) managed by the authentication provider.
    - Biometric authentication as a local device gate; biometric data never leaves the device.
3.4 The Processor shall not engage a Subprocessor without the Controller's prior authorization. The current list of Subprocessors is available in the Subprocessor Disclosure.
3.5 The Processor shall assist the Controller in responding to data subject requests (access, rectification, erasure, portability) via in-app features (Edit Profile, Delete Account, Export Data).
3.6 The Processor shall notify the Controller without undue delay upon becoming aware of a Personal Data breach affecting the Controller's data.

4. DATA RETENTION AND DELETION

4.1 Personal Data is retained according to the Controller's data retention setting (default: 365 days).
4.2 Upon account deletion by a data subject, the Processor shall permanently delete all associated Personal Data (profile, attendance records, consent records, organization memberships) within 30 days.
4.3 Upon termination of the Service by the Controller, the Processor shall delete all Personal Data associated with the Controller's organization within 90 days, unless retention is required by law.

5. AUDIT AND COMPLIANCE

5.1 The Processor shall make available to the Controller information necessary to demonstrate compliance with this DPA.
5.2 The Controller may audit the Processor's compliance with this DPA upon reasonable notice, subject to confidentiality obligations.

6. CHANGES TO THIS DPA

We may update this DPA from time to time to reflect changes in legal requirements or our processing practices. We will notify Controllers of material changes via the app or email.

7. CONTACT

For questions about this DPA, contact: privacy@attendanceguard.app
        """.trimIndent()
    )

    // ── Subprocessor Disclosure ────────────────────────────────────────

    val subprocessorDisclosure = LegalDocument(
        title = "Subprocessor Disclosure",
        version = SUBPROCESSOR_DISCLOSURE_VERSION,
        lastUpdated = "August 21, 2026",
        body = """
Last updated: August 21, 2026

This Subprocessor Disclosure lists the third-party service providers that KOSD uses to process Personal Data in connection with the ClockCard and Eventa mobile applications (the "Service"). We engage subprocessors only under data processing agreements that require confidentiality and appropriate security measures.

1. CURRENT SUBPROCESSORS

1.1 Supabase (supabase.com)
    - Role: Database hosting, authentication, and file storage
    - Data processed: Email addresses, hashed passwords, names, attendance records, location data, consent records, organization data
    - Location: Cloud-hosted; region selected by the Controller during project setup
    - Security: TLS encryption in transit, encryption at rest, row-level security policies
    - Website: https://supabase.com/security

1.2 Resend (resend.com)
    - Role: Transactional email delivery (signup confirmations, kiosk OTP codes, password reset emails)
    - Data processed: Email addresses, one-time codes, confirmation tokens
    - Location: United States
    - Security: TLS encryption, API-key authenticated, SOC 2 Type II compliant
    - Website: https://resend.com/security

1.3 Apple App Store / Google Play Store
    - Role: In-app purchase billing and subscription management
    - Data processed: Purchase transactions, subscription status, billing information (managed entirely by Apple/Google)
    - Location: Apple/Google infrastructure
    - Security: Governed by Apple's and Google's respective security and privacy frameworks
    - Websites: https://www.apple.com/legal/privacy | https://policies.google.com/privacy

2. DATA NOT SHARED WITH SUBPROCESSORS

The following data is never sent to any subprocessor:
- Biometric data (Face ID / Touch ID remains on the device)
- Device contacts, photos, camera, or microphone data
- Continuous location tracking (only point-in-time GPS at check-in/out, if enabled)

3. SUBPROCESSOR CHANGES

We will update this disclosure whenever we add, replace, or remove a subprocessor. Controllers will be notified of material changes via the app or email at least 30 days before a new subprocessor begins processing Personal Data, giving the Controller the opportunity to object.

4. SUBPROCESSOR SECURITY REQUIREMENTS

Each subprocessor must:
- Enter into a data processing agreement with KOSD that includes GDPR-compliant terms
- Maintain appropriate technical and organizational security measures
- Not process Personal Data for any purpose other than providing the contracted service
- Delete or return Personal Data upon termination of the service

5. CONTACT

For questions about subprocessors, contact: privacy@attendanceguard.app
        """.trimIndent()
    )

    // ── Refund & Cancellation Policy ──────────────────────────────────

    val refundPolicy = LegalDocument(
        title = "Refund & Cancellation Policy",
        version = REFUND_POLICY_VERSION,
        lastUpdated = "August 21, 2026",
        body = """
Last updated: August 21, 2026

This Refund & Cancellation Policy applies to subscriptions purchased through the ClockCard and Eventa mobile applications (the "Service") provided by KOSD. Subscriptions are billed through the Apple App Store or Google Play Store, and refunds are subject to the respective store's policies.

1. SUBSCRIPTION TIERS

1.1 The Service offers subscription tiers based on organization population size.
1.2 Subscription pricing and tiers are displayed within the app before purchase.

2. CANCELLATION

2.1 You can cancel your subscription at any time through:
    - Apple App Store: Settings > [Your Name] > Subscriptions > ClockCard/Eventa > Cancel Subscription
    - Google Play Store: Google Play > Menu > Subscriptions > ClockCard/Eventa > Cancel
2.2 Cancellation takes effect at the end of the current billing period. You will continue to have access to the Service until the subscription expires.
2.3 Canceling a subscription does not delete your account or data. Your organization's data is retained according to your data retention setting.

3. REFUNDS

3.1 Apple App Store: Refund requests are handled by Apple. Visit https://reportaproblem.apple.com or contact Apple Support. Apple's refund policy governs all refunds for purchases made through the App Store.
3.2 Google Play Store: Refund requests are handled by Google. Visit https://play.google.com/store/account or contact Google Play Support. Google's refund policy governs all refunds for purchases made through the Play Store.
3.3 KOSD does not process refunds directly, as all billing is managed by Apple and Google. We are unable to issue refunds or credits for subscription purchases.

4. BILLING DISPUTES

4.1 If you believe you have been charged in error, first contact Apple or Google (depending on where you purchased the subscription).
4.2 If the store is unable to resolve the issue, contact us at billing@attendanceguard.app with your purchase receipt and a description of the issue. We will assist you in resolving the dispute with the store.

5. FREE TRIALS

5.1 If a free trial is offered, you will not be charged during the trial period.
5.2 Your subscription begins automatically at the end of the trial period unless you cancel before the trial ends.
5.3 Canceling during a free trial prevents charges but does not generate a refund (since no charge was made).

6. CHANGES TO THIS POLICY

We may update this Refund & Cancellation Policy from time to time. We will notify you of material changes via the app or email.

7. CONTACT

For billing questions, contact: billing@attendanceguard.app
        """.trimIndent()
    )

    // ── Accessibility Statement ───────────────────────────────────────

    val accessibilityStatement = LegalDocument(
        title = "Accessibility Statement",
        version = ACCESSIBILITY_STATEMENT_VERSION,
        lastUpdated = "August 21, 2026",
        body = """
Last updated: August 21, 2026

KOSD is committed to making the ClockCard and Eventa mobile applications (the "Service") accessible to all users, including those with disabilities. This Accessibility Statement describes our current accessibility practices and ongoing commitments.

1. COMMITMENT TO ACCESSIBILITY

1.1 We strive to conform to the Web Content Accessibility Guidelines (WCAG) 2.1 Level AA, adapted for mobile applications, and to comply with applicable accessibility laws including the European Accessibility Act (EAA) 2025 and the Americans with Disabilities Act (ADA).
1.2 Accessibility is considered during the design and development of all new features, and we conduct regular reviews to identify and address accessibility barriers.

2. ACCESSIBILITY FEATURES

2.1 Screen Reader Support: Both apps support VoiceOver (iOS) and TalkBack (Android) for navigation, form input, and content reading.
2.2 Dynamic Type / Font Scaling: Text size adjusts to the user's system-level font settings for improved readability.
2.3 High Contrast: The apps respect system-level high contrast and dark mode settings.
2.4 Touch Targets: Interactive elements meet minimum touch target size guidelines (44x44 pt on iOS, 48x48 dp on Android).
2.5 Color and Contrast: We maintain a minimum color contrast ratio of 4.5:1 for body text and 3:1 for large text and UI components.
2.6 Reduced Motion: The apps respect the system-level "Reduce Motion" setting to minimize animations.
2.7 Haptic Feedback: Check-in and check-out actions provide haptic feedback as an alternative to visual confirmation.
2.8 Biometric Authentication: Face ID and Touch ID are supported as alternatives to password entry.

3. KNOWN LIMITATIONS

3.1 Some third-party map components (used for geofence visualization) may not fully support all screen reader gestures. We are working with the map provider to improve accessibility.
3.2 Custom-drawn graphics (such as the app logo) are decorative and do not convey essential information.
3.3 We are continuously improving accessibility and welcome user feedback to help us identify areas for improvement.

4. FEEDBACK AND REPORTING

4.1 If you encounter an accessibility barrier or have suggestions for improvement, please contact us at accessibility@attendanceguard.app. Include a description of the issue, the device and OS version you are using, and the screen where the issue occurred.
4.2 We acknowledge accessibility feedback within 5 business days and strive to resolve reported issues in a future update.

5. ONGOING EFFORTS

5.1 We conduct accessibility testing during each development cycle, including automated scanning and manual testing with assistive technologies.
5.2 We track accessibility issues and prioritize fixes based on user impact.
5.3 We monitor evolving accessibility standards and update our practices accordingly.

6. CHANGES TO THIS STATEMENT

We may update this Accessibility Statement as we improve the accessibility of our apps or as standards evolve.

7. CONTACT

For accessibility questions or feedback, contact: accessibility@attendanceguard.app
        """.trimIndent()
    )
}

// MARK: - Consent Service

enum class ConsentType(val value: String) {
    LOCATION_TRACKING("location_tracking"),
    EMPLOYEE_MONITORING("employee_monitoring"),
    TERMS_OF_SERVICE("terms_of_service"),
    PRIVACY_POLICY("privacy_policy")
}

object ConsentService {

    suspend fun recordConsent(
        type: ConsentType,
        version: String,
        consented: Boolean,
        consentText: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val params = buildJsonObject {
                put("p_consent_type", type.value)
                put("p_consent_version", version)
                put("p_consented", consented)
                if (consentText != null) put("p_consent_text", consentText)
                else put("p_consent_text", kotlinx.serialization.json.JsonNull)
            }
            client.postgrest.rpc("record_consent", params)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkConsent(type: ConsentType): Boolean = withContext(Dispatchers.IO) {
        try {
            val params = buildJsonObject {
                put("p_consent_type", type.value)
            }
            val result = client.postgrest.rpc("check_consent", params)
            val json = result.decodeSingle<JsonObject>()
            json["has_consented"]?.jsonPrimitive?.boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteAccount(): Boolean = withContext(Dispatchers.IO) {
        try {
            val params = buildJsonObject { }
            client.postgrest.rpc("delete_account", params)
            true
        } catch (e: Exception) {
            false
        }
    }
}

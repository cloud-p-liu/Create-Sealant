# Community governance

Sealant is governed through public discussion, documented decisions, and a maintainer group open to community membership. Repository ownership provides administrative custody; it does not confer a permanent technical veto. This policy starts with one bootstrap maintainer and describes how shared governance grows.

## Roles

Anyone may report issues, propose changes, review, test, document, and contribute. Contributors who demonstrate sustained judgment, constructive review, and care for compatibility can become maintainers; coding volume alone is not the criterion. Maintainers review/merge work, triage, manage releases, and enforce documented contracts and conduct.

Initial active maintainer and repository custodian: **[@cloud-p-liu](https://github.com/cloud-p-liu)**. There is currently no elected council or additional named maintainer. Release privileges and repository administration are granted explicitly with the least access needed. Becoming a policy maintainer does not automatically change GitHub permissions.

## Decisions

Routine fixes and documentation follow pull-request review. Seek consensus, record material objections, and explain the final rationale. An objection should identify concrete harm, a violated contract, or missing evidence; disagreement alone is not an indefinite veto.

For architecture, numerical semantics, persistence, major compatibility, governance, or project-wide policy, open a public proposal issue. Allow at least seven calendar days of discussion, longer when critical evidence is pending. Record alternatives, risks, tests, cost, and migration implications. Consensus may approve it after this period.

If consensus is unavailable, any active maintainer may request a public vote open for seven calendar days. Each active maintainer has one vote: approve, reject, or abstain. With at least three active maintainers, quorum requires votes from a majority of active maintainers (abstentions count toward quorum); approval requires more than half of nonabstaining votes AND at least two affirmative votes. A tie or absent quorum leaves the proposal unapproved. With two maintainers, both must approve. With only the bootstrap maintainer, that maintainer may decide after public discussion, stating the bootstrap limitation and rationale. This temporary rule ends automatically as membership grows. Interested contributors' comments and testing remain part of the decision record even when they do not vote.

## Membership and accountability

Anyone may nominate a willing contributor in a public issue, with examples of contributions, review, and conduct. Use the proposal period and voting rules above; the nominee does not vote on their own appointment. Bootstrap appointment can be made by the initial maintainer after the same public discussion. Record the outcome and update this roster before granting permissions.

A maintainer may step down at any time. After 90 days without participation, ask whether they wish to remain active and allow 14 days to respond; record inactive status rather than silently removing them. They can request reactivation through a public proposal. Inactive maintainers do not count toward voting/quorum.

Removal for repeated failure of responsibilities or conduct follows a documented proposal with opportunity to respond. The subject recuses and is excluded from the electorate for that decision. Remaining maintainers use the same thresholds on their reduced electorate, including the single-maintainer bootstrap rule if only one remains. If none remain eligible, seek an independent reviewer acceptable to the parties before a final removal decision; necessary temporary safety restrictions may still be applied under the emergency process. Handle sensitive personal details privately and publish only the minimum rationale.

## Custody and emergencies

The repository custodian administers access, branch settings, and continuity, carrying out recorded governance decisions. Before transferring custody, use a public proposal and document how issues, releases, and permissions will continue. Preserve an accessible decision history; forks remain permitted by MIT.

Maintainers may temporarily restrict access, revert a harmful change, or disable a vulnerable workflow to address an active security or availability incident. Document the reason promptly without exposing secrets, seek review within seven days, and either ratify or reverse the exception. Emergency action is not a shortcut for ordinary disputed design changes.

Conduct reports may be sent privately to a maintainer using contact channels they have chosen to make public. Conflicted maintainers recuse; seek an independent reviewer if necessary. Do not post personal allegations or sensitive security details in public issues. See CODE_OF_CONDUCT and SECURITY for reporting scope. Governance changes themselves use the public proposal process.

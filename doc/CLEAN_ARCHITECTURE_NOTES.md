# Clean Architecture Notes (Historical Context)

This repository has historically used Clean Architecture concepts for Android feature organization and layering.

Current practical status:
- The project uses a mix of legacy Android-only patterns and newer KMP patterns.
- Some legacy documentation described a fuller Clean Architecture implementation than the current codebase strictly enforces.
- During the KMP migration, architectural consistency is improving incrementally rather than being rewritten all at once.

What remains relevant:
- Keep business logic in shared/domain-oriented code where possible.
- Keep platform/framework dependencies at the edges.
- Use mappers and repository boundaries to avoid leaking transport/storage details into domain models.

If future work needs a strict architecture rewrite, this document should be expanded with:
- concrete module/layer boundaries
- dependency rules enforced by tooling
- migration checklists per feature area

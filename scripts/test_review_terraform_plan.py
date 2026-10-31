import unittest

from review_terraform_plan import PlanReviewError, review_plan


class ReviewTerraformPlanTests(unittest.TestCase):
    def test_safe_changes_produce_redacted_summary(self):
        summary = review_plan({
            "resource_changes": [
                {"address": "kubectl_manifest.backend", "change": {"actions": ["update"], "after": {"secret": "do-not-print"}}},
                {"address": "aws_eks_cluster.autoflow", "change": {"actions": ["no-op"]}},
            ]
        })
        self.assertIn("kubectl_manifest.backend", summary)
        self.assertIn("`update`", summary)
        self.assertNotIn("do-not-print", summary)
        self.assertNotIn("secret", summary)

    def test_delete_and_replacement_fail_without_explicit_authorization(self):
        for actions in (["delete"], ["delete", "create"]):
            with self.subTest(actions=actions), self.assertRaises(PlanReviewError):
                review_plan({"resource_changes": [{"address": "resource.sensitive", "change": {"actions": actions}}]})

    def test_destructive_changes_are_reported_only_with_explicit_authorization(self):
        summary = review_plan(
            {"resource_changes": [{"address": "resource.example", "change": {"actions": ["delete", "create"]}}]},
            allow_destructive=True,
        )
        self.assertIn("`delete,create`", summary)
        self.assertIn("resource.example", summary)

    def test_malformed_plan_is_rejected(self):
        with self.assertRaises(PlanReviewError):
            review_plan({"resource_changes": [{"address": "bad"}]})


if __name__ == "__main__":
    unittest.main()

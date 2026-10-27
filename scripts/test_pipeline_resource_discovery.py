from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class PipelineResourceDiscoveryTests(unittest.TestCase):
    def test_deploy_discovers_eks_and_gateway_origin_after_account_validation(self):
        workflow = (ROOT / ".github/workflows/pipeline.yml").read_text(encoding="utf-8")
        self.assertNotIn("vars.EKS_CLUSTER_NAME", workflow)
        self.assertNotIn("vars.API_GATEWAY_ORIGIN", workflow)
        self.assertIn("aws eks list-clusters", workflow)
        self.assertIn("aws apigatewayv2 get-apis", workflow)
        self.assertIn('TF_VAR_gateway_public_origin=%s', workflow)
        self.assertIn('echo "API_GATEWAY_ORIGIN=${ENDPOINTS[0]}" >> "$GITHUB_ENV"', workflow)
        plan = workflow.split("  deploy-plan:", 1)[1].split("\n  deploy-apply:", 1)[0]
        self.assertLess(plan.index("python scripts/verify_aws_account.py"), plan.index("Descobrir cluster EKS"))


if __name__ == "__main__":
    unittest.main()

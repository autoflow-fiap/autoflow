import unittest

from verify_aws_account import validate_account

MATCHING_ACCOUNT = "1" * 12
OTHER_ACCOUNT = "2" * 12


class ValidateAccountTests(unittest.TestCase):
    def test_accepts_matching_account(self):
        validate_account(MATCHING_ACCOUNT, MATCHING_ACCOUNT)

    def test_rejects_mismatched_account(self):
        with self.assertRaisesRegex(ValueError, "não corresponde"):
            validate_account(MATCHING_ACCOUNT, OTHER_ACCOUNT)

    def test_rejects_missing_expected_account(self):
        for value in (None, "", "   "):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "não está configurada"):
                validate_account(value, MATCHING_ACCOUNT)

    def test_rejects_invalid_expected_account(self):
        for value in ("123", "12345678901x", "1234567890123"):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "12 dígitos"):
                validate_account(value, MATCHING_ACCOUNT)

    def test_rejects_invalid_actual_account(self):
        with self.assertRaisesRegex(ValueError, "identidade AWS é inválida"):
            validate_account(MATCHING_ACCOUNT, "invalid")


if __name__ == "__main__":
    unittest.main()

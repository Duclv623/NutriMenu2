"""Product wording keeps uncertainty while avoiding deployment details."""
from types import SimpleNamespace

import pytest

from rag_app.config import Settings
from rag_app.domain import ChatRequest
from rag_app.meal_gateway import MockMealGateway
from rag_app.meal_models import MealContext
from rag_app.service import ChatService


@pytest.fixture
def product_service():
    snapshot = MockMealGateway().snapshot("wording")
    snapshot.data_mode = "http"
    snapshot.plan.catalog_version = "java-catalog-unversioned"
    snapshot.plan.target.bounds = {}
    snapshot.plan.target.point_targets = {"energy_kcal": 1800}
    snapshot.plan.validation_passed = False
    snapshot.plan.limitations = ["Thực đơn chưa được xác nhận đáp ứng đầy đủ nhu cầu dinh dưỡng cá nhân."]
    meals = SimpleNamespace(mode="http", snapshot=lambda *args, **kwargs: snapshot)
    service = ChatService(Settings(_env_file=None), SimpleNamespace(), meal_gateway=meals)
    return service, snapshot


def test_simple_menu_question_does_not_attach_implementation_warnings(product_service):
    service, snapshot = product_service
    response = service.chat(ChatRequest(message="Bữa trưa của tôi có những món gì?", meal_context=MealContext(plan_id=snapshot.plan.id)), "wording")
    assert response.status == "answered"
    assert "Món B" in response.answer
    assert snapshot.plan.limitations[0] not in response.warnings
    assert not any("net carb" in warning for warning in response.warnings)
    assert "backend" not in response.answer.lower()


def test_target_question_keeps_the_actual_limitation_without_backend_jargon(product_service):
    service, snapshot = product_service
    response = service.chat(ChatRequest(message="Thực đơn có đáp ứng mục tiêu của tôi không?", meal_action="explain", meal_context=MealContext(plan_id=snapshot.plan.id)), "wording")
    assert "1800 kcal" in response.answer
    assert "chưa" in response.answer.lower()
    assert "backend" not in response.answer.lower()
    assert snapshot.plan.limitations[0] in response.warnings


def test_saved_profile_fact_does_not_expose_backend_version(product_service):
    service, _ = product_service
    response = service.chat(ChatRequest(message="Tôi nặng bao nhiêu kg?"), "wording")
    assert "kg" in response.answer
    assert "backend" not in response.answer.lower()
    assert "phiên bản" not in response.answer.lower()

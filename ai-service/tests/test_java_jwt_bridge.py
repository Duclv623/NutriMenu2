"""Synchronous JWT bridge contracts; no sockets or model provider required."""
from uuid import uuid4
import httpx
import pytest
from fastapi import HTTPException, Request, Response
from rag_app.config import Settings
from rag_app.security import identity
from rag_app.meal_gateway import HttpMealGateway

def request(method='GET', headers=()):
    return Request({'type':'http','method':method,'path':'/api/status','headers':list(headers)})

def test_java_identity_uses_bearer_jwt_without_legacy_shared_key(monkeypatch):
    user = str(uuid4())
    seen = []
    def handle(req):
        seen.append(req)
        return httpx.Response(200,json={'owner':'user:'+user,'serviceUserId':99})
    client = httpx.Client(transport=httpx.MockTransport(handle))
    monkeypatch.setattr('rag_app.security.httpx.Client',lambda **kwargs: client)
    cfg = Settings(_env_file=None,gateway_auth_url='http://users.test/api/internal/identity')
    req = request(headers=[(b'authorization',b'Bearer signed-jwt')])
    assert identity(req,Response(),cfg) == 'user:'+user
    assert req.state.gateway_authorization == 'Bearer signed-jwt'
    assert seen[0].headers['Authorization'] == 'Bearer signed-jwt'
    assert 'x-gateway-key' not in seen[0].headers

def test_pdf_cookie_can_identify_a_get_but_cannot_authorize_a_post(monkeypatch):
    user = str(uuid4())
    client = httpx.Client(transport=httpx.MockTransport(lambda req: httpx.Response(200,json={'owner':'user:'+user})))
    monkeypatch.setattr('rag_app.security.httpx.Client',lambda **kwargs: client)
    cfg = Settings(_env_file=None,gateway_auth_url='http://users.test/api/internal/identity')
    assert identity(request(headers=[(b'cookie',b'nutriguide_access=signed-jwt')]),Response(),cfg) == 'user:'+user
    with pytest.raises(HTTPException) as error:
        identity(request('POST',[(b'cookie',b'nutriguide_access=signed-jwt')]),Response(),cfg)
    assert error.value.status_code == 401

def test_missing_jwt_cannot_read_another_users_plan():
    from rag_app.meal_gateway import MealServiceError
    cfg = Settings(_env_file=None,meal_service_mode='http',meal_api_base_url='http://gateway.test/api/internal/')
    with HttpMealGateway(cfg).client as client:
        assert str(client.base_url) == 'http://gateway.test/api/internal/'
    gateway = HttpMealGateway(cfg,transport=httpx.MockTransport(lambda req: pytest.fail('unauthenticated request escaped')))
    try:
        with pytest.raises(MealServiceError): gateway.snapshot('user:anyone',None,'7')
    finally: gateway.close()
